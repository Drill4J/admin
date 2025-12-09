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

import com.epam.drill.admin.common.principal.User
import com.epam.drill.admin.common.service.generateBuildId
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig.transaction
import com.epam.drill.admin.writer.rawdata.entity.*
import com.epam.drill.admin.writer.rawdata.repository.*
import com.epam.drill.admin.writer.rawdata.route.payload.*
import com.epam.drill.admin.writer.rawdata.service.RawDataWriter
import com.epam.drill.admin.writer.rawdata.views.MethodIgnoreRuleView
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

private const val EXEC_DATA_BATCH_SIZE = 100

class RawDataServiceImpl(
    private val instanceRepository: InstanceRepository,
    private val coverageRepository: CoverageRepository,
    private val testDefinitionRepository: TestDefinitionRepository,
    private val testLaunchRepository: TestLaunchRepository,
    private val methodRepository: MethodRepository,
    private val buildRepository: BuildRepository,
    private val testSessionRepository: TestSessionRepository,
    private val testSessionBuildRepository: TestSessionBuildRepository,
    private val methodIgnoreRuleRepository: MethodIgnoreRuleRepository
) : RawDataWriter {

    override suspend fun saveBuild(buildPayload: BuildPayload) {
        val build = Build(
            id = generateBuildId(
                buildPayload.groupId,
                buildPayload.appId,
                "",
                buildPayload.commitSha,
                buildPayload.buildVersion
            ),
            groupId = buildPayload.groupId,
            appId = buildPayload.appId,
            instanceId = null,
            commitSha = buildPayload.commitSha,
            buildVersion = buildPayload.buildVersion,
            branch = buildPayload.branch,
            commitDate = buildPayload.commitDate?.takeIf { it.isNotEmpty() }?.let { convertGitDefaultDateTime(it) },
            commitMessage = buildPayload.commitMessage,
            commitAuthor = buildPayload.commitAuthor
        )
        transaction {
            buildRepository.create(build)
        }
    }

    override suspend fun saveInstance(instancePayload: InstancePayload) {
        val buildId = generateBuildId(
            instancePayload.groupId,
            instancePayload.appId,
            instancePayload.instanceId,
            instancePayload.commitSha,
            instancePayload.buildVersion
        )
        val instance = Instance(
            id = instancePayload.instanceId,
            groupId = instancePayload.groupId,
            appId = instancePayload.appId,
            buildId = buildId,
            envId = instancePayload.envId
        )
        transaction {
            if (!buildRepository.existsById(instancePayload.groupId, instancePayload.appId, buildId)) {
                val build = Build(
                    id = buildId,
                    groupId = instancePayload.groupId,
                    appId = instancePayload.appId,
                    instanceId = instancePayload.instanceId,
                    commitSha = instancePayload.commitSha,
                    buildVersion = instancePayload.buildVersion,
                    branch = null,
                    commitDate = null,
                    commitMessage = null,
                    commitAuthor = null
                )
                buildRepository.create(build)
            }
            instanceRepository.create(instance)
        }
    }

    override suspend fun saveMethods(methodsPayload: MethodsPayload) {
        methodsPayload.methods.map { method ->
            val buildId = generateBuildId(
                methodsPayload.groupId,
                methodsPayload.appId,
                methodsPayload.instanceId,
                methodsPayload.commitSha,
                methodsPayload.buildVersion
            )
            // TODO add validation for fields (we had issues with body_checksum)
            Method(
                id = listOf(
                    buildId,
                    method.classname,
                    method.name,
                    method.params,
                    method.returnType
                ).joinToString(":"),
                groupId = methodsPayload.groupId,
                appId = methodsPayload.appId,
                buildId = buildId,
                classname = method.classname,
                name = method.name,
                params = method.params,
                returnType = method.returnType,
                probesCount = method.probesCount,
                probesStartPos = method.probesStartPos,
                bodyChecksum = method.bodyChecksum,
                signature = listOf(
                    method.classname,
                    method.name,
                    method.params,
                    method.returnType
                ).joinToString(":"),
                annotations = method.annotations,
                classAnnotations = method.classAnnotations
            )
        }
            .let { dataToInsert ->
                transaction {
                    methodRepository.createMany(dataToInsert)
                }
            }
    }

    override suspend fun saveCoverage(coveragePayload: CoveragePayload) {
        coveragePayload.coverage.map { coverage ->
            Coverage(
                groupId = coveragePayload.groupId,
                appId = coveragePayload.appId,
                instanceId = coveragePayload.instanceId,
                classname = coverage.classname,
                testId = coverage.testId,
                testSessionId = coverage.testSessionId,
                probes = coverage.probes
            )
        }
            .chunked(EXEC_DATA_BATCH_SIZE)
            .forEach { data ->
                transaction {
                    coverageRepository.createMany(data)
                }
            }
    }

    override suspend fun saveTestMetadata(testsPayload: AddTestsPayload) = transaction {
        testsPayload.tests.map { test ->
            TestLaunch(
                groupId = testsPayload.groupId,
                id = test.testLaunchId,
                testDefinitionId = test.testDefinitionId,
                testSessionId = testsPayload.sessionId,
                result = test.result.toString(),
                duration = test.duration
            )
        }.let { testLaunchRepository.createMany(it) }

        testsPayload.tests.map { test ->
            TestDefinition(
                groupId = testsPayload.groupId,
                id = test.testDefinitionId,
                type = "placeholder", // TODO replace once it's implemented on autotest agent
                runner = test.details.runner,
                name = test.details.testName,
                path = test.details.path,
                tags = test.details.tags,
                metadata = test.details.metadata
            )
        }.let { testDefinitionRepository.createMany(it) }
    }

    override suspend fun saveTestSession(sessionPayload: SessionPayload, user: User?) {
        val testSession = TestSession(
            id = sessionPayload.id,
            groupId = sessionPayload.groupId,
            testTaskId = sessionPayload.testTaskId,
            startedAt = sessionPayload.startedAt.toLocalDateTime(TimeZone.UTC).toJavaLocalDateTime(),
            createdBy = user?.username
        )
        transaction {
            testSessionRepository.create(testSession)
            testSessionBuildRepository.deleteAllByTestSessionId(sessionPayload.id)
            sessionPayload.builds.forEach { buildInfo ->
                val buildId = generateBuildId(
                    sessionPayload.groupId,
                    buildInfo.appId,
                    buildInfo.instanceId,
                    buildInfo.commitSha,
                    buildInfo.buildVersion
                )
                testSessionBuildRepository.create(sessionPayload.id, buildId, sessionPayload.groupId)
            }
        }
    }

    override suspend fun saveMethodIgnoreRule(rulePayload: MethodIgnoreRulePayload) {
        val rule = MethodIgnoreRule(
            groupId = rulePayload.groupId,
            appId = rulePayload.appId,
            namePattern = rulePayload.namePattern,
            classnamePattern = rulePayload.classnamePattern,
            annotationsPattern = rulePayload.annotationsPattern,
            classAnnotationsPattern = rulePayload.classAnnotationsPattern
        )
        transaction {
            methodIgnoreRuleRepository.create(rule)
        }
    }

    override suspend fun getAllMethodIgnoreRules(): List<MethodIgnoreRuleView> {
        return transaction {
            methodIgnoreRuleRepository.getAll()
        }
    }

    override suspend fun deleteMethodIgnoreRuleById(ruleId: Int) {
        transaction {
            methodIgnoreRuleRepository.deleteById(ruleId)
        }
    }

    private fun convertGitDefaultDateTime(commitDate: String): LocalDateTime {
        return ZonedDateTime.parse(commitDate, DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy Z", Locale.ENGLISH))
            .toLocalDateTime()
    }
}
