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
package com.epam.drill.admin.metrics.service.impl

import com.epam.drill.admin.metrics.config.MetricsDatabaseConfig.transaction
import com.epam.drill.admin.metrics.exception.BuildNotFound
import com.epam.drill.admin.metrics.exception.InvalidParameters
import com.epam.drill.admin.metrics.repository.MetricsRepository
import com.epam.drill.admin.metrics.service.MetricsService

class MetricsServiceImpl(private val metricsRepository: MetricsRepository) : MetricsService {

    override suspend fun getBuildDiffReport(
        groupId: String,
        appId: String,
        instanceId: String?,
        commitSha: String?,
        buildVersion: String?,
        baselineInstanceId: String?,
        baselineCommitSha: String?,
        baselineBuildVersion: String?,
        coverageThreshold: Double
    ): Map<String, Any> {
        return transaction {

            val baselineBuildId = generateBuildId(
                groupId,
                appId,
                baselineInstanceId,
                baselineCommitSha,
                baselineBuildVersion,
                """
                Provide at least one the following: baselineInstanceId, baselineCommitSha, baselineBuildVersion
                """.trimIndent()
            )

            if (!metricsRepository.buildExists(baselineBuildId)) {
                throw BuildNotFound("Baseline build info not found for $baselineBuildId")
            }

            val buildId = generateBuildId(groupId, appId, instanceId, commitSha, buildVersion)
            if (!metricsRepository.buildExists(buildId)) {
                throw BuildNotFound("Build info not found for $buildId")
            }

            val metrics = metricsRepository.getBuildDiffReport(buildId, baselineBuildId, coverageThreshold)

            mapOf(
                "inputParameters" to mapOf(
                    "groupId" to groupId,
                    "appId" to appId,
                    "instanceId" to instanceId,
                    "commitSha" to commitSha,
                    "buildVersion" to buildVersion,
                    "baselineInstanceId" to baselineInstanceId,
                    "baselineCommitSha" to baselineCommitSha,
                    "baselineBuildVersion" to baselineBuildVersion
                ),
                "inferredValues" to mapOf(
                    "build" to buildId,
                    "baselineBuild" to baselineBuildId,
                ),
                "metrics" to metrics
            )
        }
    }

    // TODO remove duplicate in RawDataRepositoryImpl
    private fun generateBuildId(
        groupId: String,
        appId: String,
        instanceId: String?,
        commitSha: String?,
        buildVersion: String?,
        errorMsg: String = "Provide at least one of the following: instanceId, commitSha or buildVersion"
    ): String {
        if (groupId.isBlank()) { throw InvalidParameters("groupId cannot be empty or blank") }

        if (appId.isBlank()) { throw InvalidParameters("appId cannot be empty or blank") }

        if (instanceId.isNullOrBlank() && commitSha.isNullOrBlank() && buildVersion.isNullOrBlank()) {
            throw InvalidParameters(errorMsg)
        }

        return listOf(
            groupId,
            appId,
            listOf(buildVersion, commitSha, instanceId).first { !it.isNullOrBlank() }
        ).joinToString(":")
    }
}
