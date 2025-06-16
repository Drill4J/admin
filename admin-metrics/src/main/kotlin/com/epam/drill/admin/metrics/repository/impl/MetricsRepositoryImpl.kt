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

    override suspend fun getMethodsCoverage(
        buildId: String,
        testTag: String?,
        envId: String?,
        branch: String?,
        packageNamePattern: String?,
        classNamePattern: String?
    ): List<Map<String, Any?>> = transaction {
        executeQueryReturnMap(
            """
                WITH MethodsCoverage AS
                (
                	SELECT 
                        classname,
                        name,
                        params,
                        return_type,
                        probes_count,
                        aggregated_covered_probes
                	FROM raw_data.get_methods_coverage_v2(
                        input_build_id => ?,
                        input_aggregated_coverage => TRUE,
                        input_materialized => TRUE,
                        input_test_tag => ?,
                        input_env_id => ?,
                        input_branch => ?,                        
                        input_package_name_pattern => ?,
                        input_class_name_pattern => ?                        
                	)
                )
                SELECT
                	classname || '/' || name AS name,
                	params,
                	return_type,
                	SUM(probes_count) AS probes_count,
                	SUM(aggregated_covered_probes) AS covered_probes
                FROM MethodsCoverage
                GROUP BY name, classname, params, return_type
                ORDER BY classname DESC
            """.trimIndent(),
            buildId,
            testTag.takeUnless { it.isNullOrBlank() },
            envId.takeUnless { it.isNullOrBlank() },
            branch.takeUnless { it.isNullOrBlank() },
            "$packageNamePattern%".takeIf { !packageNamePattern.isNullOrBlank() },
            "%$classNamePattern".takeIf { !classNamePattern.isNullOrBlank() }
        )
    }

    override suspend fun getBuildDiffReport(
        targetBuildId: String,
        baselineBuildId: String,
        coverageThreshold: Double,
        useMaterializedViews: Boolean
    ) = transaction {
        executeQueryReturnMap(
            """
                    WITH 
                    Risks AS (
                        SELECT * 
                        FROM raw_data.get_methods_coverage_v2(                        
                            input_build_id => ?, 
                            input_baseline_build_id => ?,
                            input_aggregated_coverage => true,
                            input_materialized => false
                        )	
                    ),
                    RecommendedTests AS (
                        SELECT *
                        FROM raw_data.get_recommended_tests_v4(
                            input_target_build_id => ?, 
                            input_baseline_build_id => ?,
                            input_materialized => ?
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
            targetBuildId,
            baselineBuildId,
            targetBuildId,
            baselineBuildId,
            useMaterializedViews
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
        coveragePeriodFrom: LocalDateTime?,
        useMaterializedViews: Boolean
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
                    input_materialized => ?
                )
            """.trimIndent(),
            targetBuildId,
            testsToSkip,
            testTaskId,
            baselineBuildId,
            coveragePeriodFrom,
            useMaterializedViews
        )
    }
}
