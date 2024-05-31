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
package com.epam.drill.admin.metrics.repository.impl

import com.epam.drill.admin.metrics.config.MetricsDatabaseConfig.transaction
import com.epam.drill.admin.metrics.config.executeQuery
import com.epam.drill.admin.metrics.config.executeQueryReturnMap
import com.epam.drill.admin.metrics.repository.MetricsRepository
import kotlinx.serialization.json.JsonObject

enum class BuildDiffResult {
    BASELINE_BUILD_MISSING,
    CURRENT_BUILD_MISSING
}

class MetricsRepositoryImpl : MetricsRepository {
    override suspend fun getRisksByBranchDiff(
        groupId: String,
        appId: String,
        currentBranch: String,
        currentVcsRef: String,
        baseBranch: String,
        baseVcsRef: String
    ): List<JsonObject> = transaction {
        executeQuery(
            """
                SELECT * 
                  FROM raw_data.get_risks_by_branch_diff(?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            groupId,
            appId,
            currentVcsRef,
            currentBranch,
            baseBranch,
            baseVcsRef
        )
    }

    override suspend fun getTotalCoverage(groupId: String, appId: String, currentVcsRef: String): JsonObject = transaction {
        executeQuery(
            """
                SELECT raw_data.calculate_total_coverage_percent(?, ?, ?) as coverage
            """.trimIndent(),
            groupId,
            appId,
            currentVcsRef
        ).first()
    }

    override suspend fun getBuildDiffReport(
        groupId: String,
        appId: String,
        instanceId: String?,
        commitSha: String?,
        buildVersion: String?,
        baselineInstanceId: String?,
        baselineCommitSha: String?,
        baselineBuildVersion: String?
    ): Map<String, Any> {
        return transaction {

            val baselineBuildExists = executeQueryReturnMap(
                """
                SELECT raw_data.check_build_exists(?, ?, ?, ?, ?)
                """.trimIndent(),
                groupId,
                appId,
                baselineInstanceId.toString(),
                baselineCommitSha.toString(),
                baselineBuildVersion.toString()
            )
            if (baselineBuildExists[0]?.get("check_build_exists")?.toString() != "true") {
                throw IllegalStateException(BuildDiffResult.BASELINE_BUILD_MISSING.toString()) // TODO descriptive message
            }
            val baselineBuildId = generateBuildId(groupId, appId, baselineInstanceId, baselineCommitSha, baselineBuildVersion)

            val buildExists = executeQueryReturnMap(
                """
                SELECT raw_data.check_build_exists(?, ?, ?, ?, ?)
                """.trimIndent(),
                groupId,
                appId,
                instanceId.toString(),
                commitSha.toString(),
                buildVersion.toString()
            )
            if (buildExists[0]?.get("check_build_exists")?.toString() != "true") {
                throw IllegalStateException(BuildDiffResult.CURRENT_BUILD_MISSING.toString()) // TODO descriptive message
            }
            val buildId = generateBuildId(groupId, appId, instanceId, commitSha, buildVersion)

            val risksAccumulatedCoverage = executeQueryReturnMap(
                """
                SELECT * FROM raw_data.get_build_risks_accumulated_coverage(?,?)
                """.trimIndent(),
                buildId,
                baselineBuildId
            )

            val recommendedTests = executeQueryReturnMap(
                """
                SELECT * FROM raw_data.get_recommended_tests(?,?)
                """.trimIndent(),
                buildId,
                baselineBuildId
            )

            val totalCoveragePercent = executeQueryReturnMap(
                """
                SELECT raw_data.get_total_coverage_percent(?)
                """.trimIndent(),
                buildId
            )

            val coverageByPackages = executeQueryReturnMap(
                """
                SELECT * FROM raw_data.get_coverage_by_packages(?)
                """.trimIndent(),
                buildId
            )

            val coverageByMethods = executeQueryReturnMap(
                """
                SELECT * FROM raw_data.get_coverage_by_methods(?)
                """.trimIndent(),
                buildId
            )

            val coverageByClasses = executeQueryReturnMap(
                """
                SELECT * FROM raw_data.get_coverage_by_classes(?)
                """.trimIndent(),
                buildId
            )

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
                "metrics" to mapOf(
                    "totalCoveragePercent" to totalCoveragePercent,
                    "recommendedTests" to recommendedTests,
                    "risksAccumulatedCoverage" to risksAccumulatedCoverage,
                    "coverageByPackages" to coverageByPackages,
                    "coverageByClasses" to coverageByClasses,
                    "coverageByMethods" to coverageByMethods
                )
            )
        }
    }

    override suspend fun getSummaryByBranchDiff(
        groupId: String,
        appId: String,
        currentBranch: String,
        currentVcsRef: String,
        baseBranch: String,
        baseVcsRef: String
    ): JsonObject = transaction {
        executeQuery(
            """
                SELECT raw_data.calculate_total_coverage_percent(?, ?, ?) as coverage,
                       (SELECT count(*) 
                          FROM raw_data.get_risks_by_branch_diff(?, ?, ?, ?, ?, ?)) as risks
            """.trimIndent(),

            groupId,
            appId,
            currentVcsRef,

            groupId,
            appId,
            currentVcsRef,
            currentBranch,
            baseBranch,
            baseVcsRef
        ).first()
    }

    // TODO remove duplicate (copied from RawDataRepositoryImpl)!
    private fun generateBuildId(
        groupId: String,
        appId: String,
        instanceId: String?,
        commitSha: String?,
        buildVersion: String?
    ): String {
        require(groupId.isNotBlank()) { "groupId cannot be empty or blank" }
        require(appId.isNotBlank()) { "appId cannot be empty or blank" }
        require(!instanceId.isNullOrBlank() || !commitSha.isNullOrBlank() || !buildVersion.isNullOrBlank()) {
            "provide at least one of the following: instanceId, commitSha or buildVersion"
        }

        val buildIdElements = mutableListOf(groupId, appId)
        val firstNotBlank = listOf(buildVersion, commitSha, instanceId).first { !it.isNullOrBlank() }
        buildIdElements.add(firstNotBlank as String) // TODO think of better way to convince typesystem its not null
        return buildIdElements.joinToString(":")
    }
}