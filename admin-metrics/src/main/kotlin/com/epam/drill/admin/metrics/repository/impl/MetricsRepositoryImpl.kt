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

    override suspend fun getApplications(groupId: String?): List<Map<String, Any>> = transaction {
        val query = buildString {
            append(
                """
                SELECT DISTINCT
                    group_id,
                    app_id
                FROM raw_data.builds                
                """.trimIndent()
            )
            if (groupId != null) {
                append(" WHERE builds.group_id = ?")
            }
        }
        val params = mutableListOf<Any>().apply {
            if (groupId != null) add(groupId)
        }

        executeQueryReturnMap(query, *params.toTypedArray()) as List<Map<String, Any>>
    }

    override suspend fun getBuilds(
        groupId: String, appId: String,
        branch: String?, envId: String?,
        offset: Int, limit: Int
    ): List<Map<String, Any>> = transaction {
        val query = buildString {
            append(
                """
            SELECT 
                build_id,
                group_id,
                app_id,
                env_ids,
                build_version,
                branch,
                commit_sha,                                
                commit_author,
                commit_message,
                committed_at
            FROM raw_data.matview_builds builds
            WHERE builds.group_id = ? AND builds.app_id = ?
            """.trimIndent()
            )
            if (branch != null) {
                append(" AND builds.branch = ?")
            }
            if (envId != null) {
                append(" AND ? = ANY(builds.env_ids)")
            }
            append(" ORDER BY created_at DESC")
            append(" OFFSET ? LIMIT ?")
        }

        val params = mutableListOf<Any>().apply {
            add(groupId)
            add(appId)
            if (branch != null) add(branch)
            if (envId != null) add(envId)
            add(offset)
            add(limit)
        }

        executeQueryReturnMap(query, *params.toTypedArray()) as List<Map<String, Any>>
    }

    override suspend fun getBuildsCount(
        groupId: String, appId: String,
        branch: String?, envId: String?
    ): Long = transaction {
        val query = buildString {
            append(
                """
            SELECT COUNT(*) AS cnt
            FROM raw_data.matview_builds builds
            WHERE builds.group_id = ? AND builds.app_id = ?
            """.trimIndent()
            )
            if (branch != null) {
                append(" AND builds.branch = ?")
            }
            if (envId != null) {
                append(" AND ? = ANY(builds.env_ids)")
            }
        }
        val params = mutableListOf<Any>().apply {
            add(groupId)
            add(appId)
            if (branch != null) add(branch)
            if (envId != null) add(envId)
        }
        val result = executeQueryReturnMap(query, *params.toTypedArray())
        (result[0]["cnt"] as? Number)?.toLong() ?: 0
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
        buildId: String,
        baselineBuildId: String
    ) = transaction {
        executeQueryReturnMap(
            """
                    WITH 
                    Changes AS (
                        SELECT 
                            equal,
                            modified,
                            added,
                            deleted
                        FROM raw_data.get_builds_compared_to_baseline_v2(
			                input_build_id => ?,
			                input_baseline_build_id => ?
                        ) AS baseline   
                    ),   
                    Coverage AS (
                        SELECT
                            isolated_probes_coverage_ratio,
                            aggregated_probes_coverage_ratio,
                            isolated_tested_methods,
                            isolated_missed_methods,
                            aggregated_tested_methods,
                            aggregated_missed_methods
                        FROM raw_data.get_build_coverage_v3(
                            input_build_id => ?,
                            input_baseline_build_id => ?,
                            input_coverage_in_other_builds => true
                        )
                    ),
                    RecommendedTests AS (
                        SELECT count(*) AS tests_to_run
                        FROM raw_data.get_recommended_tests_v4(
                            input_target_build_id => ?, 
                            input_tests_to_skip => false,
                            input_materialized => true
                        )
                    )	
                    SELECT 
                        (SELECT added FROM Changes) as changes_new_methods,
                        (SELECT modified FROM Changes) as changes_modified_methods,
                        (SELECT deleted FROM Changes) as changes_deleted_methods,
                        (SELECT added + modified FROM Changes) as total_changes,
                        (SELECT aggregated_tested_methods FROM Coverage) as tested_changes,
                        (SELECT aggregated_probes_coverage_ratio FROM Coverage) as coverage,
                        (SELECT tests_to_run FROM RecommendedTests) as recommended_tests
                """.trimIndent(),
            buildId,
            baselineBuildId,
            buildId,
            baselineBuildId,
            buildId
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
                    input_materialized => TRUE
                )
            """.trimIndent(),
            targetBuildId,
            testsToSkip,
            testTaskId,
            baselineBuildId,
            coveragePeriodFrom
        )
    }

    override suspend fun getChanges(
        buildId: String,
        baselineBuildId: String,
        offset: Int,
        limit: Int
    ): List<Map<String, Any?>> = transaction {
        executeQueryReturnMap(
            """
                SELECT 
                    classname,
                    name,
                    params,
                    return_type,
                    change_type,
                    probes_count,                    
                    isolated_covered_probes,
                    aggregated_covered_probes,                    
                    isolated_probes_coverage_ratio,
                    aggregated_probes_coverage_ratio                    
                FROM raw_data.get_methods_coverage_v2(
                    input_build_id => ?,
                    input_baseline_build_id => ?,
                    input_aggregated_coverage => TRUE,
                    input_materialized => TRUE
                )                
                OFFSET ? LIMIT ?
            """.trimIndent(),
            buildId,
            baselineBuildId,
            offset,
            limit
        )
    }

    override suspend fun getChangesCount(buildId: String, baselineBuildId: String): Long = transaction {
        val result = executeQueryReturnMap(
            """
                SELECT COUNT(*) AS cnt
                FROM raw_data.get_methods_coverage_v2(
                    input_build_id => ?,
                    input_baseline_build_id => ?,
                    input_aggregated_coverage => TRUE,
                    input_materialized => TRUE
                )
            """.trimIndent(),
            buildId,
            baselineBuildId
        )
        (result.firstOrNull()?.get("cnt") as? Number)?.toLong() ?: 0
    }
}
