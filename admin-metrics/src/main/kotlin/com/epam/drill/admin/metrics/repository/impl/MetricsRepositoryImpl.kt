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
        executeQueryReturnMap(
            "SELECT true FROM metrics.builds WHERE build_id = ?",
            buildId
        ).isNotEmpty()
    }

    override suspend fun getApplications(groupId: String?): List<Map<String, Any?>> = transaction {
        executeQueryReturnMap {
            append(
                """
                SELECT DISTINCT
                    group_id,
                    app_id
                FROM metrics.builds                
                """.trimIndent()
            )
            appendOptional(" WHERE builds.group_id = ?", groupId)
        }
    }

    override suspend fun getBuilds(
        groupId: String, appId: String,
        branch: String?, envId: String?,
        offset: Int?, limit: Int?
    ): List<Map<String, Any?>> = transaction {
        executeQueryReturnMap {
            append(
                """
            SELECT 
                b.build_id,
                b.group_id,
                b.app_id,
                b.version_id,
                b.app_env_ids,
                b.build_version,
                b.branch,
                b.commit_sha,                                
                b.commit_author,
                b.commit_message,
                b.committed_at,
                b.created_at
            FROM metrics.builds b
            WHERE b.group_id = ? AND b.app_id = ?
            """.trimIndent(), groupId, appId
            )
            appendOptional(" AND b.branch = ?", branch)
            appendOptional(" AND ? = ANY(b.app_env_ids)", envId)
            appendOptional(" OFFSET ?", offset)
            appendOptional(" LIMIT ?", limit)
        }
    }

    override suspend fun getBuildsCount(
        groupId: String, appId: String,
        branch: String?, envId: String?
    ): Long = transaction {
        val result = executeQueryReturnMap {
            append(
                """
            SELECT COUNT(*) AS cnt
            FROM metrics.builds b
            WHERE b.group_id = ? AND b.app_id = ?
            """.trimIndent(), groupId, appId
            )
            appendOptional(" AND b.branch = ?", branch)
            appendOptional(" AND ? = ANY(b.env_ids)", envId)
        }
        (result[0]["cnt"] as? Number)?.toLong() ?: 0
    }

    override suspend fun getMethodsWithCoverage(
        buildId: String,
        coverageTestTag: String?,
        coverageEnvId: String?,
        coverageBranch: String?,
        packageName: String?,
        className: String?,
        offset: Int?, limit: Int?
    ): List<Map<String, Any?>> = transaction {
        executeQueryReturnMap {
            append(
                """
                SELECT 
                    class_name,
                    method_name,
                    method_params,
                    return_type,                    
                    probes_count,                    
                    isolated_covered_probes,
                    aggregated_covered_probes,                    
                    isolated_probes_coverage_ratio,
                    aggregated_probes_coverage_ratio                    
                FROM metrics.get_methods_with_coverage(
                    input_build_id => ?
                """.trimIndent(), buildId
            )
            appendOptional(", input_package_name_pattern => ?", packageName) { "$it%" }
            appendOptional(", input_class_name_pattern => ?", className) { "%$it" }
            appendOptional(", input_coverage_test_tags => ?", coverageTestTag) { listOf(it) }
            appendOptional(", input_coverage_app_env_ids => ?", coverageEnvId) { listOf(it) }
            appendOptional(", input_coverage_branches => ?", coverageBranch) { listOf(it) }
            append(
                """
                ) 
                ORDER BY signature    
                """.trimIndent()
            )
            appendOptional(" OFFSET ?", offset)
            appendOptional(" LIMIT ?", limit)
        }
    }

    override suspend fun getChangesWithCoverage(
        buildId: String,
        baselineBuildId: String?,
        coverageTestTag: String?,
        coverageEnvId: String?,
        coverageBranch: String?,
        packageName: String?,
        className: String?,
        offset: Int?,
        limit: Int?
    ): List<Map<String, Any?>> = transaction {
        executeQueryReturnMap {
            append(
                """
                SELECT 
                    class_name,
                    method_name,
                    method_params,
                    return_type,
                    change_type,
                    probes_count,                    
                    isolated_covered_probes,
                    aggregated_covered_probes,                    
                    isolated_probes_coverage_ratio,
                    aggregated_probes_coverage_ratio                    
                FROM metrics.get_changes_with_coverage(
                    input_build_id => ?
                """.trimIndent(), buildId
            )
            appendOptional(", input_baseline_build_id => ?", baselineBuildId)
            appendOptional(", input_package_name_pattern => ?", packageName) { "$it%" }
            appendOptional(", input_class_name_pattern => ?", className) { "%$it" }
            appendOptional(", input_coverage_test_tags => ?", coverageTestTag) { arrayOf(it) }
            appendOptional(", input_coverage_app_env_ids => ?", coverageEnvId) { arrayOf(it) }
            appendOptional(", input_coverage_branches => ?", coverageBranch) { arrayOf(it) }
            append(
                """
                ) 
                ORDER BY signature    
                """.trimIndent()
            )
            appendOptional(" OFFSET ?", offset)
            appendOptional(" LIMIT ?", limit)
        }
    }

    override suspend fun getMethodsCount(
        buildId: String,
        packageNamePattern: String?,
        classNamePattern: String?
    ): Long = transaction {
        val result = executeQueryReturnMap {
            append(
                """
                SELECT COUNT(*) AS cnt
                FROM metrics.get_methods(
                    input_build_id => ?
            """.trimIndent(), buildId
            )
            appendOptional(", input_package_name_pattern => ?", packageNamePattern) { "$it%" }
            appendOptional(", input_class_name_pattern => ?", classNamePattern) { "%$it" }
            append(
                """
                )
            """.trimIndent()
            )
        }
        (result.firstOrNull()?.get("cnt") as? Number)?.toLong() ?: 0
    }

    override suspend fun getChangesCount(
        buildId: String,
        baselineBuildId: String?,
        packageNamePattern: String?,
        classNamePattern: String?
    ): Long = transaction {
        val result = executeQueryReturnMap {
            append(
                """
                SELECT COUNT(*) AS cnt
                FROM metrics.get_changes(
                    input_build_id => ?,
                    include_deleted => false,
                    include_equal => false
            """.trimIndent(), buildId
            )
            appendOptional(", input_baseline_build_id => ?", baselineBuildId)
            appendOptional(", input_package_name_pattern => ?", packageNamePattern) { "$it%" }
            appendOptional(", input_class_name_pattern => ?", classNamePattern) { "%$it" }
            append(
                """
                )
            """.trimIndent()
            )
        }
        (result.firstOrNull()?.get("cnt") as? Number)?.toLong() ?: 0
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
                            COUNT(CASE WHEN change_type = 'equal' THEN 1 END) AS equal,
                            COUNT(CASE WHEN change_type = 'modified' THEN 1 END) AS modified,                            
                            COUNT(CASE WHEN change_type = 'new' THEN 1 END) AS added,
                            COUNT(CASE WHEN change_type = 'deleted' THEN 1 END) AS deleted
                        FROM metrics.get_changes(
			                input_build_id => ?,
			                input_baseline_build_id => ?,
                            include_deleted => true,  
                            include_equal => true
                        ) m   
                    ),   
                    Coverage AS (
                        SELECT
                            isolated_probes_coverage_ratio,
                            aggregated_probes_coverage_ratio,
                            isolated_tested_methods,
                            isolated_missed_methods,
                            aggregated_tested_methods,
                            aggregated_missed_methods
                        FROM metrics.get_builds_with_coverage(
                            input_build_id => ?,
                            input_baseline_build_id => ?                            
                        )
                    ),
                    RecommendedTests AS (
                        SELECT count(*) AS tests_to_run
                        FROM metrics.get_recommended_tests(
                            input_build_id => ?, 
                            tests_to_skip => false                            
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


    override suspend fun getRecommendedTests(
        targetBuildId: String,
        baselineBuildId: String?,
        testsToSkip: Boolean,
        testTaskId: String?,
        coveragePeriodFrom: LocalDateTime?,
    ): List<Map<String, Any?>> = transaction {
        executeQueryReturnMap {
            append(
                """
                SELECT                     
                    test_definition_id,
                    test_path,
                    test_name,
                    test_metadata
                FROM metrics.get_recommended_tests(                    
                    input_build_id => ?,
                    tests_to_skip => ?                
            """.trimIndent()
            , targetBuildId, testsToSkip)
            appendOptional(", input_test_task_ids => ?", testTaskId) { listOf(it) }
            appendOptional(", input_coverage_period_from => ?", coveragePeriodFrom)
            append("""
                )
            """.trimIndent())
        }
    }

    override suspend fun getImpactedTests(
        targetBuildId: String,
        baselineBuildId: String,
        testTaskId: String?,
        testTag: String?,
        testPathPattern: String?,
        testNamePattern: String?,
        offset: Int?,
        limit: Int?
    ): List<Map<String, Any?>> = transaction {
        executeQueryReturnMap {
            append(
                """
                SELECT 
                    test_definition_id,
                    test_path,
                    test_name,                    
                    impacted_methods                 
                FROM metrics.get_impacted_tests(
                    input_build_id => ?,
                    input_baseline_build_id => ?
                    """.trimIndent(), targetBuildId, baselineBuildId)
            appendOptional(", input_test_task_ids => ?", testTaskId) { listOf(it) }
            appendOptional(", input_test_tags => ?", testTag) { listOf(it) }
            appendOptional(", input_test_path_pattern => ?", testPathPattern) { "$it%" }
            appendOptional(", input_test_name_pattern => ?", testNamePattern) { "$it%" }
            append("""
                )
            """.trimIndent())
            appendOptional(" OFFSET ?", offset)
            appendOptional(" LIMIT ?", limit)
        }
    }

    override suspend fun getImpactedMethods(
        targetBuildId: String,
        baselineBuildId: String,
        testTaskId: String?,
        testTag: String?,
        testPathPattern: String?,
        testNamePattern: String?,
        offset: Int?,
        limit: Int?
    ): List<Map<String, Any?>>  = transaction {
        executeQueryReturnMap {
            append(
                """
                SELECT 
                    group_id,
                    app_id,
                    class_name,
                    method_name,
                    method_params,
                    return_type                 
                FROM metrics.get_impacted_methods(
                    input_build_id => ?,
                    input_baseline_build_id => ?
                    """.trimIndent(), targetBuildId, baselineBuildId)
            appendOptional(", input_test_task_ids => ?", testTaskId) { listOf(it) }
            appendOptional(", input_test_tags => ?", testTag) { listOf(it) }
            appendOptional(", input_test_path_pattern => ?", testPathPattern) { "$it%" }
            appendOptional(", input_test_name_pattern => ?", testNamePattern) { "$it%" }
            append("""
                )
            """.trimIndent())
            appendOptional(" OFFSET ?", offset)
            appendOptional(" LIMIT ?", limit)
        }
    }

    override suspend fun refreshMaterializedView(viewName: String, concurrently: Boolean) = transaction {
        executeUpdate(
            """             
            REFRESH MATERIALIZED VIEW ${if (concurrently) "CONCURRENTLY" else ""} $viewName;
            """.trimIndent()
        )
    }
}
