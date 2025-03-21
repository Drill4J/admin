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
import com.epam.drill.admin.metrics.config.executeQueryReturnMap
import com.epam.drill.admin.metrics.config.executeUpdate
import com.epam.drill.admin.metrics.repository.MetricsRepository
import java.time.LocalDateTime

class MetricsRepositoryImpl : MetricsRepository {

    override suspend fun buildExists(
        buildId: String
    ): Boolean = transaction {
        val x = executeQueryReturnMap(
            """
                SELECT raw_data.check_build_exists(?)
                """.trimIndent(),
            buildId
        )
        x[0]["check_build_exists"] == true
    }

    override suspend fun getBuilds(groupId: String, appId: String, branch: String?): List<Map<String, Any>> = transaction {
        val query = buildString {
            append(
            """
            SELECT *
            FROM raw_data.builds builds
            WHERE builds.group_id = ? AND builds.app_id = ?
            """.trimIndent()
            )
            if (branch != null) {
                append(" AND builds.branch = ?")
            }
            append(" ORDER BY created_at DESC")
        }

        val params = mutableListOf<Any>().apply {
            add(groupId)
            add(appId)
            if (branch != null) add(branch)
        }

        executeQueryReturnMap(query, *params.toTypedArray()) as List<Map<String, Any>>
    }

    override suspend fun getBuildDiffReport(
        groupId: String,
        appId: String,
        targetBuildId: String,
        baselineBuildId: String,
        coverageThreshold: Double
    ) = transaction {
        executeQueryReturnMap(
            """
                    WITH 
                    Risks AS (
                        SELECT * 
                        FROM raw_data.get_methods_coverage(
                            input_group_id => ?,
                            input_app_id => ?,
                            input_build_id => ?, 
                            input_baseline_build_id => ?,
                            input_aggregated_coverage => true                            
                        )	
                    ),
                    RecommendedTests AS (
                        SELECT *
                        FROM raw_data.get_recommended_tests_v4(
                            input_target_build_id => ?, 
                            input_baseline_build_id => ?,
                            input_materialized => false
                        )
                    )	
                    SELECT 
                        (SELECT count(*) FROM Risks WHERE change_type = 'new') as changes_new_methods,
                        (SELECT count(*) FROM Risks WHERE change_type = 'modified') as changes_modified_methods,
                        (SELECT count(*) FROM Risks) as total_changes,
                        (SELECT count(*) FROM Risks WHERE aggregated_probes_coverage_ratio > 0) as tested_changes,
                        (SELECT CAST(SUM(aggregated_covered_probes) AS FLOAT) / SUM(probes_count) FROM Risks) as coverage,
                        (SELECT count(*) FROM RecommendedTests) as recommended_tests
                """.trimIndent(),
            groupId,
            appId,
            targetBuildId,
            baselineBuildId,
            targetBuildId,
            baselineBuildId
        ).first() as Map<String, String>
    }

    override suspend fun refreshMaterializedView(viewName: String) = transaction {
        executeUpdate(
            """             
            REFRESH MATERIALIZED VIEW CONCURRENTLY raw_data.$viewName;
            """.trimIndent()
        )
    }

    override suspend fun getRecommendedTests(
        targetBuildId: String,
        baselineBuildId: String?,
        testsToSkip: Boolean,
        testTaskId: String?,
        coveragePeriodFrom: LocalDateTime
    ): List<Map<String, Any?>> = transaction {
        executeQueryReturnMap(
            """            
                SELECT 
                    test_definition_id,
                    runner,
                    path,
                    name,                    
                    tags,
                    metadata
                FROM raw_data.get_recommended_tests_v4(                    
                    input_target_build_id => ?,
                    input_tests_to_skip => ?,
                    input_test_task_id => ?,		
                    input_baseline_build_id => ?,
                    input_coverage_period_from => ?,
                    input_materialized => false
                )
            """.trimIndent(),
            targetBuildId,
            testsToSkip,
            testTaskId,
            baselineBuildId,
            coveragePeriodFrom
        )
    }
}
