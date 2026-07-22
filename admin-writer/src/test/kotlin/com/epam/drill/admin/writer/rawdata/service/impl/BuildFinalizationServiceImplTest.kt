/**
 * Copyright 2020 - 2022 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.drill.admin.writer.rawdata.service.impl

import com.epam.drill.admin.common.service.generateBuildId
import com.epam.drill.admin.test.DatabaseTests
import com.epam.drill.admin.test.withRollback
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig
import com.epam.drill.admin.writer.rawdata.entity.BuildValidationStatus
import com.epam.drill.admin.writer.rawdata.entity.Method
import com.epam.drill.admin.writer.rawdata.repository.impl.BuildRepositoryImpl
import com.epam.drill.admin.writer.rawdata.repository.impl.CoverageRepositoryImpl
import com.epam.drill.admin.writer.rawdata.repository.impl.InstanceRepositoryImpl
import com.epam.drill.admin.writer.rawdata.repository.impl.MethodRepositoryImpl
import com.epam.drill.admin.writer.rawdata.repository.impl.TestDefinitionRepositoryImpl
import com.epam.drill.admin.writer.rawdata.repository.impl.TestLaunchRepositoryImpl
import com.epam.drill.admin.writer.rawdata.repository.impl.TestSessionBuildRepositoryImpl
import com.epam.drill.admin.writer.rawdata.repository.impl.TestSessionRepositoryImpl
import com.epam.drill.admin.writer.rawdata.route.payload.BuildFinalizePayload
import com.epam.drill.admin.writer.rawdata.route.payload.MethodsPayload
import com.epam.drill.admin.writer.rawdata.route.payload.SingleMethodPayload
import com.epam.drill.admin.writer.rawdata.service.BuildValidationService
import com.epam.drill.admin.writer.rawdata.util.combineChecksumsCrc64
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class BuildFinalizationServiceImplTest : DatabaseTests({ RawDataWriterDatabaseConfig.init(it) }) {

    private val buildRepository = BuildRepositoryImpl()
    private val methodRepository = MethodRepositoryImpl()

    private fun rawDataWriter(buildValidationService: BuildValidationService) = RawDataServiceImpl(
        instanceRepository = InstanceRepositoryImpl(),
        coverageRepository = CoverageRepositoryImpl(),
        testDefinitionRepository = TestDefinitionRepositoryImpl(),
        testLaunchRepository = TestLaunchRepositoryImpl(),
        methodRepository = methodRepository,
        buildRepository = buildRepository,
        testSessionRepository = TestSessionRepositoryImpl(),
        testSessionBuildRepository = TestSessionBuildRepositoryImpl(),
        buildValidationService = buildValidationService,
    )

    private fun buildId(groupId: String, appId: String, buildVersion: String) =
        generateBuildId(groupId, appId, null, null, buildVersion)

    private suspend fun seedMethods(groupId: String, appId: String, buildId: String, checksums: List<String>) {
        val data = checksums.mapIndexed { index, checksum ->
            Method(
                groupId = groupId,
                appId = appId,
                buildId = buildId,
                methodId = "method-$checksum",
                classname = "com.example.TestClass",
                name = "method$checksum",
                params = "",
                returnType = "void",
                probesCount = 1,
                probesStartPos = index,
                bodyChecksum = checksum,
                signature = "com.example.TestClass:method$checksum::void",
            )
        }
        // Seeded in its own (committed) transaction: BuildFinalizationServiceImpl opens its own
        // transaction internally (a separate DB connection/session), so data inserted within the
        // surrounding `withRollback` transaction (uncommitted) wouldn't be visible to it otherwise.
        RawDataWriterDatabaseConfig.transaction { methodRepository.createMany(data) }
    }

    @Test
    fun `finalize should mark build VALID when methods count and checksum match`() = withRollback {
        val groupId = "test-group"
        val appId = "test-app"
        val buildVersion = "1.0.0"
        val id = buildId(groupId, appId, buildVersion)
        val checksums = listOf("100", "200", "300")
        seedMethods(groupId, appId, id, checksums)

        val service = BuildValidationServiceImpl(buildRepository, methodRepository)
        val status = rawDataWriter(service).finalizeBuild(
            BuildFinalizePayload(
                groupId = groupId,
                appId = appId,
                buildVersion = buildVersion,
                methodsCount = checksums.size,
                methodsChecksum = combineChecksumsCrc64(checksums),
            )
        )

        assertEquals(BuildValidationStatus.VALID, status)
        assertEquals(BuildValidationStatus.VALID, buildRepository.getStatus(groupId, appId, id))
    }

    @Test
    fun `finalize should keep build PENDING while the actual methods count is less than expected`() = withRollback {
        val groupId = "test-group"
        val appId = "test-app"
        val buildVersion = "2.0.0"
        val id = buildId(groupId, appId, buildVersion)
        val checksums = listOf("100", "200", "300")
        // Only part of the methods have arrived so far (e.g. queue hasn't caught up yet)
        seedMethods(groupId, appId, id, checksums.take(1))

        val service = BuildValidationServiceImpl(buildRepository, methodRepository)
        val status = rawDataWriter(service).finalizeBuild(
            BuildFinalizePayload(
                groupId = groupId,
                appId = appId,
                buildVersion = buildVersion,
                methodsCount = checksums.size, // expected count is higher than what's actually stored
                methodsChecksum = combineChecksumsCrc64(checksums),
            )
        )

        assertEquals(BuildValidationStatus.PENDING, status)
        assertEquals(BuildValidationStatus.PENDING, buildRepository.getStatus(groupId, appId, id))

        // As long as the actual methods count stays below the expected one, retries must keep the build PENDING
        seedMethods(groupId, appId, id, checksums.drop(1).take(1))
        val stillInProgressStatus = service.validateBuildById(groupId, appId, id)
        assertEquals(BuildValidationStatus.PENDING, stillInProgressStatus)
        assertEquals(BuildValidationStatus.PENDING, buildRepository.getStatus(groupId, appId, id))

        // Once the actual methods count reaches the expected one, the build is finalized
        seedMethods(groupId, appId, id, checksums.drop(2))
        val finalStatus = service.validateBuildById(groupId, appId, id)
        assertEquals(BuildValidationStatus.VALID, finalStatus)
        assertEquals(BuildValidationStatus.VALID, buildRepository.getStatus(groupId, appId, id))
    }

    @Test
    fun `finalize should mark build INVALID without retry when the actual methods count is greater than expected`() =
        withRollback {
            val groupId = "test-group"
            val appId = "test-app"
            val buildVersion = "2.1.0"
            val id = buildId(groupId, appId, buildVersion)
            val checksums = listOf("100", "200", "300")
            // More methods are stored than the agent reported (e.g. leftover/leaked from elsewhere)
            seedMethods(groupId, appId, id, checksums)

            val service = BuildValidationServiceImpl(buildRepository, methodRepository)
            val status = rawDataWriter(service).finalizeBuild(
                BuildFinalizePayload(
                    groupId = groupId,
                    appId = appId,
                    buildVersion = buildVersion,
                    methodsCount = checksums.size - 1, // expected count is lower than what's actually stored
                    methodsChecksum = combineChecksumsCrc64(checksums.dropLast(1)),
                )
            )

            assertEquals(BuildValidationStatus.INVALID, status)
            assertEquals(BuildValidationStatus.INVALID, buildRepository.getStatus(groupId, appId, id))
            // Marked INVALID immediately, on the very first attempt, without any retry
            assertNotNull(buildRepository.getById(groupId, appId, id)!!.validatedAt)
        }

    @Test
    fun `finalize should mark build INVALID when methods count matches but checksum mismatches`() =
        withRollback {
            val groupId = "test-group"
            val appId = "test-app"
            val buildVersion = "3.0.0"
            val id = buildId(groupId, appId, buildVersion)
            val checksums = listOf("100", "200")
            seedMethods(groupId, appId, id, checksums)

            val service = BuildValidationServiceImpl(buildRepository, methodRepository)
            val status = rawDataWriter(service).finalizeBuild(
                BuildFinalizePayload(
                    groupId = groupId,
                    appId = appId,
                    buildVersion = buildVersion,
                    methodsCount = checksums.size, // count matches, so a checksum mismatch is not retryable
                    methodsChecksum = "not-the-real-checksum",
                )
            )

            assertEquals(BuildValidationStatus.INVALID, status)
            assertEquals(BuildValidationStatus.INVALID, buildRepository.getStatus(groupId, appId, id))
        }

    @Test
    fun `validateBuild should mark build INVALID once the max validation window elapses`() = withRollback {
        val groupId = "test-group"
        val appId = "test-app"
        val buildVersion = "4.0.0"
        val id = buildId(groupId, appId, buildVersion)
        val checksums = listOf("100", "200")
        seedMethods(groupId, appId, id, checksums)

        // A zero-length window means any elapsed time since finalizedAt already exceeds it
        val service = BuildValidationServiceImpl(buildRepository, methodRepository, maxValidationWindow = Duration.ZERO)
        // Attempt #1 (via finalize): count mismatches and the window is already exceeded -> INVALID
        val firstStatus = rawDataWriter(service).finalizeBuild(
            BuildFinalizePayload(
                groupId = groupId,
                appId = appId,
                buildVersion = buildVersion,
                methodsCount = checksums.size + 1,
                methodsChecksum = combineChecksumsCrc64(checksums),
            )
        )

        assertEquals(BuildValidationStatus.INVALID, firstStatus)
        assertEquals(BuildValidationStatus.INVALID, buildRepository.getStatus(groupId, appId, id))
    }

    @Test
    fun `validateBuild should keep retrying while the max validation window has not elapsed yet`() = withRollback {
        val groupId = "test-group"
        val appId = "test-app"
        val buildVersion = "4.1.0"
        val id = buildId(groupId, appId, buildVersion)
        val checksums = listOf("100", "200")
        seedMethods(groupId, appId, id, checksums.take(1))

        val service = BuildValidationServiceImpl(
            buildRepository,
            methodRepository,
            maxValidationWindow = Duration.ofHours(1),
        )
        val firstStatus = rawDataWriter(service).finalizeBuild(
            BuildFinalizePayload(
                groupId = groupId,
                appId = appId,
                buildVersion = buildVersion,
                methodsCount = checksums.size,
                methodsChecksum = combineChecksumsCrc64(checksums),
            )
        )
        assertEquals(BuildValidationStatus.PENDING, firstStatus)

        // Retried by the fixed-interval job while still within the validation window
        val secondStatus = service.validateBuildById(groupId, appId, id)

        assertEquals(BuildValidationStatus.PENDING, secondStatus)
        assertEquals(BuildValidationStatus.PENDING, buildRepository.getStatus(groupId, appId, id))
    }

    @Test
    fun `validateBuild should mark build VALID once methods eventually arrive`() = withRollback {
        val groupId = "test-group"
        val appId = "test-app"
        val buildVersion = "5.0.0"
        val id = buildId(groupId, appId, buildVersion)
        val checksums = listOf("100", "200")
        // Only the first method has arrived so far (e.g. queue hasn't caught up yet)
        seedMethods(groupId, appId, id, checksums.take(1))

        val service = BuildValidationServiceImpl(buildRepository, methodRepository)
        val firstStatus = rawDataWriter(service).finalizeBuild(
            BuildFinalizePayload(
                groupId = groupId,
                appId = appId,
                buildVersion = buildVersion,
                methodsCount = checksums.size,
                methodsChecksum = combineChecksumsCrc64(checksums),
            )
        )
        assertEquals(BuildValidationStatus.PENDING, firstStatus)

        // The rest of the methods finally arrive
        seedMethods(groupId, appId, id, checksums.drop(1))

        val secondStatus = service.validateBuildById(groupId, appId, id)

        assertEquals(BuildValidationStatus.VALID, secondStatus)
    }

    @Test
    fun `saveMethods should reject new methods once build is VALID`() = withRollback {
        val groupId = "test-group"
        val appId = "test-app"
        val buildVersion = "6.0.0"
        val id = buildId(groupId, appId, buildVersion)
        val checksums = listOf("100", "200")
        seedMethods(groupId, appId, id, checksums)

        val service = BuildValidationServiceImpl(buildRepository, methodRepository)
        val writer = rawDataWriter(service)
        val status = writer.finalizeBuild(
            BuildFinalizePayload(
                groupId = groupId,
                appId = appId,
                buildVersion = buildVersion,
                methodsCount = checksums.size,
                methodsChecksum = combineChecksumsCrc64(checksums),
            )
        )
        assertEquals(BuildValidationStatus.VALID, status)

        assertFailsWith(IllegalStateException::class) {
            writer.saveMethods(
                MethodsPayload(
                    groupId = groupId,
                    appId = appId,
                    buildVersion = buildVersion,
                    methods = arrayOf(
                        SingleMethodPayload(
                            classname = "com.example.NewClass",
                            name = "newMethod",
                            params = "",
                            returnType = "void",
                            probesCount = 1,
                            probesStartPos = 0,
                            bodyChecksum = "999",
                        )
                    )
                )
            )
        }
    }
}
