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
import com.epam.drill.admin.metrics.repository.MetricsRepository
import com.epam.drill.admin.metrics.config.executeQuery
import kotlinx.serialization.json.JsonObject

class MetricsRepositoryImpl : MetricsRepository {
    override suspend fun getRisksByBranchDiff(
        groupId: String,
        agentId: String,
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
            agentId,
            currentVcsRef,
            currentBranch,
            baseBranch,
            baseVcsRef
        )
    }

    override suspend fun getTotalCoverage(groupId: String, agentId: String, currentVcsRef: String): JsonObject = transaction {
        executeQuery(
            """
                SELECT raw_data.calculate_total_coverage_percent(?, ?, ?) as coverage
            """.trimIndent(),
            groupId,
            agentId,
            currentVcsRef
        ).first()
    }

    override suspend fun getSummaryByBranchDiff(
        groupId: String,
        agentId: String,
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
            agentId,
            currentVcsRef,

            groupId,
            agentId,
            currentVcsRef,
            currentBranch,
            baseBranch,
            baseVcsRef
        ).first()
    }
}