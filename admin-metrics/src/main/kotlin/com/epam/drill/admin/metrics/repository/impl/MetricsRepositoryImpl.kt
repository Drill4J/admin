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
import com.epam.drill.admin.metrics.repository.MetricsRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class MetricsRepositoryImpl : MetricsRepository {
    override suspend fun getBuildDiffReport(
        groupId: String,
        appId: String,
        instanceId: String?,
        commitSha: String?,
        buildVersion: String?,
        baselineInstanceId: String?,
        baselineCommitSha: String?,
        baselineBuildVersion: String?
    ): JsonObject {
        transaction {
            val baselineBuildExists = executeQuery(
                """
                SELECT raw_data.check_build_exists(?, ?, ?, ?, ?)
            """.trimIndent(),
                groupId,
                appId,
                baselineInstanceId.toString(),
                baselineCommitSha.toString(),
                baselineBuildVersion.toString()
            )

            val buildExists = executeQuery(
                """
                SELECT raw_data.check_build_exists(?, ?, ?, ?, ?)
            """.trimIndent(),
                groupId,
                appId,
                instanceId.toString(),
                commitSha.toString(),
                buildVersion.toString()
            )
            println(baselineBuildExists.size.toString())
            println(buildExists.size.toString())
        }

        val myMap = mapOf("foo" to "bar")
        return JsonObject(myMap.map { (key, value) -> key to JsonPrimitive(value) }.toMap())
    }
//    override suspend fun getBuildDiffReport(
//        groupId: String,
//        appId: String,
//        instanceId: String?,
//        commitSha: String?,
//        buildVersion: String?,
//        baselineInstanceId: String?,
//        baselineCommitSha: String?,
//        baselineBuildVersion: String?
//    ): List<JsonObject> = transaction {
//

//
//        transaction {
//            executeQuery(
//                """
//                SELECT *
//                  FROM raw_data.get_risks_by_branch_diff(?, ?, ?, ?, ?, ?)
//            """.trimIndent(),
//                groupId,
//                appId,
//                currentVcsRef,
//                currentBranch,
//                baseBranch,
//                baseVcsRef
//            )
//        }

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
}