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

import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig.transaction
import com.epam.drill.admin.writer.rawdata.entity.Build
import com.epam.drill.admin.writer.rawdata.entity.BuildValidationStatus
import com.epam.drill.admin.writer.rawdata.repository.BuildRepository
import com.epam.drill.admin.writer.rawdata.repository.MethodRepository
import com.epam.drill.admin.writer.rawdata.service.BuildValidationService
import com.epam.drill.admin.writer.rawdata.util.InvalidChecksumException
import com.epam.drill.admin.writer.rawdata.util.combineChecksumsCrc64
import mu.KotlinLogging
import java.time.Duration
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}
private const val RETRY_BATCH_LIMIT = 200

class BuildValidationServiceImpl(
    private val buildRepository: BuildRepository,
    private val methodRepository: MethodRepository,
    private val maxValidationWindow: Duration = Duration.ofHours(1),
) : BuildValidationService {

    override suspend fun validateBuildById(groupId: String, appId: String, buildId: String): BuildValidationStatus {
        val build = buildRepository.getById(groupId, appId, buildId)
            ?: error("Build [$buildId] not found, cannot validate.")
        return validateBuild(build)
    }

    override suspend fun validateAllBuilds() {
        val builds = transaction {
            buildRepository.findBuildsToRetry(RETRY_BATCH_LIMIT)
        }
        if (builds.isEmpty()) return

        logger.info { "Retrying finalization validation for ${builds.size} build(s)..." }
        builds.forEach { build ->
            transaction { validateBuild(build) }
        }
    }

    private suspend fun validateBuild(build: Build): BuildValidationStatus {
        val expectedMethodsCount = build.methodsCount
        val expectedBuildChecksum = build.buildChecksum
        val finalizedAt = build.finalizedAt
        if (expectedMethodsCount == null || expectedBuildChecksum == null || finalizedAt == null) {
            logger.warn { "Build [${build.id}] has no finalization data to validate against, skipping." }
            return build.status
        }

        val now = LocalDateTime.now()
        return when (validateBuildIntegrity(build, expectedMethodsCount, expectedBuildChecksum)) {
            ValidationResult.VALID -> {
                buildRepository.updateBuildStatus(
                    groupId = build.groupId,
                    appId = build.appId,
                    buildId = build.id,
                    status = BuildValidationStatus.VALID,
                )
                logger.info { "Build [${build.id}] finalized successfully." }
                BuildValidationStatus.VALID
            }
            ValidationResult.METHODS_LESS_THAN_EXPECTED -> {
                if (Duration.between(finalizedAt, now) >= maxValidationWindow) {
                    buildRepository.updateBuildStatus(
                        groupId = build.groupId,
                        appId = build.appId,
                        buildId = build.id,
                        status = BuildValidationStatus.INVALID,
                    )
                    logger.warn { "Build [${build.id}] marked as INVALID: validation window ($maxValidationWindow) since finalization elapsed without the expected methods arriving." }
                    BuildValidationStatus.INVALID
                } else {
                    buildRepository.updateBuildStatus(
                        groupId = build.groupId,
                        appId = build.appId,
                        buildId = build.id,
                        status = BuildValidationStatus.PENDING,
                    )
                    logger.debug { "Build [${build.id}] validation failed, will be retried by the next scheduled run." }
                    BuildValidationStatus.PENDING
                }
            }
            ValidationResult.METHODS_GREATER_THAN_EXPECTED -> {
                buildRepository.updateBuildStatus(
                    groupId = build.groupId,
                    appId = build.appId,
                    buildId = build.id,
                    status = BuildValidationStatus.INVALID,
                )
                logger.warn { "Build [${build.id}] marked as INVALID: actual methods count is greater than expected." }
                BuildValidationStatus.INVALID
            }
            ValidationResult.CHECKSUM_MISMATCH -> {
                buildRepository.updateBuildStatus(
                    groupId = build.groupId,
                    appId = build.appId,
                    buildId = build.id,
                    status = BuildValidationStatus.INVALID,
                )
                logger.warn { "Build [${build.id}] marked as INVALID: methods count matches but checksum doesn't." }
                BuildValidationStatus.INVALID
            }
        }
    }

    private enum class ValidationResult {
        VALID, METHODS_LESS_THAN_EXPECTED, METHODS_GREATER_THAN_EXPECTED, CHECKSUM_MISMATCH
    }

    private suspend fun validateBuildIntegrity(
        build: Build,
        expectedMethodsCount: Int,
        expectedBuildChecksum: String,
    ): ValidationResult {
        val actualMethodsCount = methodRepository.countByBuildId(build.groupId, build.appId, build.id)
        if (actualMethodsCount < expectedMethodsCount) {
            return ValidationResult.METHODS_LESS_THAN_EXPECTED
        }
        if (actualMethodsCount > expectedMethodsCount) {
            return ValidationResult.METHODS_GREATER_THAN_EXPECTED
        }
        val checksums = methodRepository.getBodyChecksumsByBuildId(build.groupId, build.appId, build.id)
        val checksumMatches = try {
            combineChecksumsCrc64(checksums) == expectedBuildChecksum
        } catch (e: InvalidChecksumException) {
            logger.warn(e) { "Build [${build.id}] contains a method with an invalid checksum." }
            false
        }
        return if (checksumMatches) ValidationResult.VALID else ValidationResult.CHECKSUM_MISMATCH
    }
}
