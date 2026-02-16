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
import com.epam.drill.admin.metrics.models.SortOrder
import com.epam.drill.admin.metrics.repository.MetricsRepository
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC

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
            append(" ORDER BY COALESCE(b.committed_at, b.created_at) DESC ")
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
                    signature,
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
        limit: Int?,
        includeDeleted: Boolean?,
        includeEqual: Boolean?
    ): List<Map<String, Any?>> = transaction {
        executeQueryReturnMap {
            append(
                """
                SELECT 
                    signature,
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
            appendOptional(", include_deleted => ?", includeDeleted) { it }
            appendOptional(", include_equal => ?", includeEqual) { it }
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
        baselineBuildId: String,
        coverageThreshold: Double,
    ): Map<String, Any?> = transaction {
        val result = executeQueryReturnMap {
            append(
                """
                    WITH 
                    Changes AS (
                        SELECT                             
                            COUNT(CASE WHEN change_type = 'modified' THEN 1 END) AS modified,                            
                            COUNT(CASE WHEN change_type = 'new' THEN 1 END) AS added,
                            COUNT(CASE WHEN change_type = 'deleted' THEN 1 END) AS deleted
                        FROM metrics.get_changes(
			                input_build_id => ?,
			                input_baseline_build_id => ?,
                            include_deleted => true
                        ) m   
                    ),
                    """.trimIndent(), buildId, baselineBuildId
            )
            append(
                """
                    TestedChanges AS (
                        SELECT
                            change_type,
                            COUNT(*) AS tested_methods                                                                                    
                        FROM metrics.get_changes_with_coverage(
                            input_build_id => ?,
                            input_baseline_build_id => ?,
                            input_coverage_test_results => array['PASSED']
                        )
                        WHERE aggregated_covered_probes > ?
                        GROUP BY change_type
                    ),
            """.trimIndent(), buildId, baselineBuildId, coverageThreshold
            )
            append(
                """
                    Coverage AS (
                        SELECT
                            isolated_probes_coverage_ratio,
                            aggregated_probes_coverage_ratio                            
                        FROM metrics.get_builds_with_coverage(
                            input_build_id => ?,
                            input_baseline_build_id => ?                            
                        )
                    ),
            """.trimIndent(), buildId, baselineBuildId
            )
            append(
                """
                    TestLaunches AS (
                        SELECT 
                            tl.test_definition_id,
                            MIN(tl.test_result) AS test_result
                        FROM metrics.test_launches tl
                        JOIN metrics.test_sessions ts ON ts.test_session_id = tl.test_session_id AND ts.group_id = tl.group_id
                        JOIN metrics.test_session_builds tsb ON tsb.test_session_id = ts.test_session_id AND tsb.group_id = ts.group_id
                        WHERE tsb.build_id = ?
                            AND tl.test_result IN ('PASSED', 'FAILED')	
                        GROUP BY tl.test_definition_id		
                    ),
            """.trimIndent(), buildId
            )
            append(
                """
                    ImpactedTests AS (
                        SELECT 
                            test_definition_id, 
                            group_id
                        FROM metrics.get_impacted_tests_v2(
                            input_build_id => ?,
                            input_baseline_build_id => ?
                        )
                    ),  
            """.trimIndent(), buildId, baselineBuildId
            )
            append("""
                    ImpactedTestsWithResults AS (
                        SELECT  
                            COUNT(*) AS impacted_tests,
		                    SUM(CASE WHEN test_result = 'PASSED' THEN 1 ELSE 0 END) AS passed_impacted_tests,
		                    SUM(CASE WHEN test_result = 'FAILED' THEN 1 ELSE 0 END) AS failed_impacted_tests
                        FROM ImpactedTests it	
                        LEFT JOIN TestLaunches tl ON tl.test_definition_id = it.test_definition_id	
                    ) 
            """.trimIndent())
            append(
                """                    
                   SELECT 
                        (SELECT added FROM Changes) as changes_new_methods,
                        (SELECT modified FROM Changes) as changes_modified_methods,
                        (SELECT deleted FROM Changes) as changes_deleted_methods,
                        COALESCE((SELECT tested_methods FROM TestedChanges WHERE change_type = 'new'), 0) as tested_new_methods,
                        COALESCE((SELECT tested_methods FROM TestedChanges WHERE change_type = 'modified'), 0) as tested_modified_methods,
                        (SELECT isolated_probes_coverage_ratio FROM Coverage) as coverage,                                                                        
                        (SELECT aggregated_probes_coverage_ratio FROM Coverage) as aggregated_coverage,
                        (SELECT impacted_tests FROM ImpactedTestsWithResults) AS impacted_tests,
                    	(SELECT passed_impacted_tests FROM ImpactedTestsWithResults) AS passed_impacted_tests,
                    	(SELECT failed_impacted_tests FROM ImpactedTestsWithResults) AS failed_impacted_tests
                """.trimIndent()
            )
        }
        result.firstOrNull() ?: emptyMap()
    }


    override suspend fun getRecommendedTests(
        targetBuildId: String,
        testImpactStatuses: List<String>,

        baselineBuildIds: List<String>,
        baselineFromBuildId: String?,
        baselineUntilBuildId: String?,
        baselineBuildBranches: List<String>,

        testTaskIds: List<String>,
        testTags: List<String>,
        testPathPattern: String?,
        testNamePattern: String?,

        packageNamePattern: String?,
        classNamePattern: String?,

        coverageAppEnvIds: List<String>,
        coveragePeriodFrom: LocalDateTime?,
        coveragePeriodUntil: LocalDateTime?,

        offset: Int?,
        limit: Int?
    ): List<Map<String, Any?>> = transaction {
        executeQueryReturnMap {
            append(
                """
                SELECT                     
                    test_definition_id,
                    test_runner,
                    test_path,
                    test_name,
                    test_tags,
                    test_metadata,
                    test_impact_status,
                    impacted_methods,
                    baseline_build_id
                FROM metrics.get_recommended_tests_v2(                    
                    input_build_id => ?,
                    input_test_impact_statuses => ?                
            """.trimIndent(), targetBuildId, testImpactStatuses
            )

            appendOptional(", input_baseline_build_ids => ?", baselineBuildIds)
            appendOptional(", input_baseline_from_build_id => ?", baselineFromBuildId)
            appendOptional(", input_baseline_until_build_id => ?", baselineUntilBuildId)
            appendOptional(", input_baseline_build_branches => ?", baselineBuildBranches)

            appendOptional(", input_test_task_ids => ?", testTaskIds)
            appendOptional(", input_test_tags => ?", testTags)
            appendOptional(", input_test_path_pattern => ?", testPathPattern) { "$it%" }
            appendOptional(", input_test_name_pattern => ?", testNamePattern) { "$it%" }

            appendOptional(", input_package_pattern => ?", packageNamePattern) { "$it%" }
            appendOptional(", input_class_name_pattern => ?", classNamePattern) { "$it%" }

            appendOptional(", input_coverage_app_env_ids => ?", coverageAppEnvIds)
            appendOptional(", input_coverage_period_from => ?", coveragePeriodFrom)
            appendOptional(", input_coverage_period_until => ?", coveragePeriodUntil)
            append(
                """
                )
            """.trimIndent()
            )
            appendOptional(" OFFSET ?", offset)
            appendOptional(" LIMIT ?", limit)
        }
    }

    override suspend fun getImpactedTests(
        targetBuildId: String,
        baselineBuildId: String,

        testTaskId: String?,
        testTags: List<String>,
        testPathPattern: String?,
        testNamePattern: String?,

        packageNamePattern: String?,
        methodSignaturePattern: String?,
        excludeMethodSignatures: List<String>,

        coverageBranches: List<String>,
        coverageAppEnvIds: List<String>,

        sortBy: String?,
        sortOrder: SortOrder?,

        offset: Int?,
        limit: Int?
    ): List<Map<String, Any?>> = transaction {
        val sortDirection = sortOrder?.name ?: "ASC"

        executeQueryReturnMap {
            append(
                """
                SELECT 
                    test_definition_id,
                    test_path,
                    test_name,      
                    test_runner,
                    test_tags,
                    test_metadata,
                    impacted_methods                 
                FROM metrics.get_impacted_tests_v2(
                    input_build_id => ?,
                    input_baseline_build_id => ?
                    """.trimIndent(), targetBuildId, baselineBuildId
            )
            appendOptional(", input_test_task_id => ?", testTaskId)
            appendOptional(", input_test_tags => ?", testTags)
            appendOptional(", input_test_path_pattern => ?", testPathPattern) { "$it%" }
            appendOptional(", input_test_name_pattern => ?", testNamePattern) { "$it%" }

            appendOptional(", input_package_name_pattern => ?", packageNamePattern) { "$it%" }
            appendOptional(", input_method_signature_pattern => ?", methodSignaturePattern)
            appendOptional(", input_exclude_method_signatures => ?", excludeMethodSignatures)

            appendOptional(", input_coverage_branches => ?", coverageBranches)
            appendOptional(", input_coverage_app_env_ids => ?", coverageAppEnvIds)

            append(
                """
                )
            """.trimIndent()
            )

            if (sortBy != null) {
                append(" ORDER BY $sortBy $sortDirection")
            }

            appendOptional(" OFFSET ?", offset)
            appendOptional(" LIMIT ?", limit)
        }
    }

    override suspend fun getImpactedMethods(
        targetBuildId: String,
        baselineBuildId: String,

        testTaskId: String?,
        testTags: List<String>,
        testPathPattern: String?,
        testNamePattern: String?,

        packageNamePattern: String?,
        methodSignaturePattern: String?,
        excludeMethodSignatures: List<String>,

        coverageBranches: List<String>,
        coverageAppEnvIds: List<String>,

        sortBy: String?,
        sortOrder: SortOrder?,

        offset: Int?,
        limit: Int?
    ): List<Map<String, Any?>> = transaction {
        val sortDirection = sortOrder?.name ?: "ASC"

        executeQueryReturnMap {
            append(
                """
                SELECT 
                    group_id,
                    app_id,
                    signature,
                    class_name,
                    method_name,
                    method_params,
                    return_type,
                    impacted_tests
                FROM metrics.get_impacted_methods_v2(
                    input_build_id => ?,
                    input_baseline_build_id => ?
                    """.trimIndent(), targetBuildId, baselineBuildId
            )
            appendOptional(", input_test_task_id => ?", testTaskId)
            appendOptional(", input_test_tags => ?", testTags)
            appendOptional(", input_test_path_pattern => ?", testPathPattern) { "$it%" }
            appendOptional(", input_test_name_pattern => ?", testNamePattern) { "$it%" }

            appendOptional(", input_package_name_pattern => ?", packageNamePattern)
            appendOptional(", input_method_signature_pattern => ?", methodSignaturePattern)
            appendOptional(", input_exclude_method_signatures => ?", excludeMethodSignatures)

            appendOptional(", input_coverage_branches => ?", coverageBranches)
            appendOptional(", input_coverage_app_env_ids => ?", coverageAppEnvIds)

            append(
                """
                )
            """.trimIndent()
            )

            if (sortBy != null) {
                append(" ORDER BY $sortBy $sortDirection")
            }
            appendOptional(" OFFSET ?", offset)
            appendOptional(" LIMIT ?", limit)
        }
    }

    override suspend fun getMetricsPeriodDays(): Map<String, Instant> = transaction {
        executeQueryReturnMap(
            """             
                SELECT 
                    group_id, 
                    metrics.get_metrics_period(group_id) AS metrics_period
                FROM raw_data.builds
                GROUP BY group_id
                 """.trimIndent()
        ).associate {
            it["group_id"] as String to (it["metrics_period"] as LocalDateTime).toInstant(UTC)
        }
    }

    override suspend fun deleteAllBuildDataCreatedBefore(groupId: String, timestamp: Instant) = transaction {
        val timestamp = Timestamp.from(timestamp)
        executeUpdate(
            """
                DELETE FROM metrics.build_method_test_definition_coverage c
                WHERE EXISTS (SELECT 1 
                    FROM metrics.builds b 
                    WHERE b.updated_at_day < ?
                        AND b.group_id = c.group_id
                        AND b.app_id = c.app_id
                        AND b.build_id = c.build_id
                )
                """.trimIndent(), timestamp
        )
        executeUpdate(
            """
                DELETE FROM metrics.build_method_test_session_coverage c
                WHERE EXISTS (SELECT 1 
                    FROM metrics.builds b 
                    WHERE b.updated_at_day < ?
                        AND b.group_id = c.group_id
                        AND b.app_id = c.app_id
                        AND b.build_id = c.build_id
                )
                """.trimIndent(), timestamp
        )
        executeUpdate(
            """
                DELETE FROM metrics.build_method_coverage c
                WHERE EXISTS (SELECT 1 
                    FROM metrics.builds b 
                    WHERE b.updated_at_day < ?
                        AND b.group_id = c.group_id
                        AND b.app_id = c.app_id
                        AND b.build_id = c.build_id
                )
                """.trimIndent(), timestamp
        )
        executeUpdate(
            """
                DELETE FROM metrics.test_session_builds tsb
                WHERE EXISTS (SELECT 1 
                    FROM metrics.builds b 
                    WHERE b.updated_at_day < ?
                        AND b.group_id = tsb.group_id
                        AND b.app_id = tsb.app_id
                        AND b.build_id = tsb.build_id
                )
                """.trimIndent(), timestamp
        )
        executeUpdate(
            """
                DELETE FROM metrics.build_methods bm
                WHERE EXISTS (SELECT 1 
                    FROM metrics.builds b 
                    WHERE b.updated_at_day < ?
                        AND b.group_id = bm.group_id
                        AND b.app_id = bm.app_id
                        AND b.build_id = bm.build_id
                )
                """.trimIndent(), timestamp
        )
        executeUpdate(
            """
                DELETE FROM metrics.methods m
                WHERE m.created_at_day < ?
                    AND NOT EXISTS (SELECT 1 
                        FROM metrics.build_methods bm 
                        WHERE bm.group_id = m.group_id 
                            AND bm.app_id = m.app_id
                            AND bm.method_id = m.method_id
                    )
                """.trimIndent(), timestamp
        )
        executeUpdate("DELETE FROM metrics.builds WHERE updated_at_day < ?", timestamp)
    }

    override suspend fun deleteAllTestDataCreatedBefore(groupId: String, timestamp: Instant) = transaction {
        val timestamp = Timestamp.from(timestamp)
        executeUpdate("DELETE FROM metrics.test_launches WHERE created_at_day < ?", timestamp)
        executeUpdate(
            """
                DELETE FROM metrics.build_method_test_definition_coverage c
                WHERE EXISTS (SELECT 1 
                    FROM metrics.test_sessions ts 
                    WHERE ts.created_at_day < ?
                        AND ts.group_id = c.group_id
                        AND ts.test_session_id = c.test_session_id
                )
                """.trimIndent(), timestamp
        )
        executeUpdate(
            """
                DELETE FROM metrics.build_method_test_session_coverage c
                WHERE EXISTS (SELECT 1 
                    FROM metrics.test_sessions ts 
                    WHERE ts.created_at_day < ?
                        AND ts.group_id = c.group_id
                        AND ts.test_session_id = c.test_session_id
                )   
                """.trimIndent(), timestamp
        )
        executeUpdate(
            """
                DELETE FROM metrics.test_session_builds tsb
                WHERE EXISTS (SELECT 1 
                    FROM metrics.test_sessions ts 
                    WHERE ts.created_at_day < ?
                        AND ts.group_id = tsb.group_id
                        AND ts.test_session_id = tsb.test_session_id
                )
                """.trimIndent(), timestamp
        )
        executeUpdate("DELETE FROM metrics.test_sessions WHERE created_at_day < ?", timestamp)
        executeUpdate(
            """
                DELETE FROM metrics.build_method_test_definition_coverage c
                WHERE EXISTS (SELECT 1 
                    FROM metrics.test_definitions td 
                    WHERE td.updated_at_day < ?
                        AND td.group_id = c.group_id
                        AND td.test_definition_id = c.test_definition_id
                )
                """.trimIndent(), timestamp
        )
        executeUpdate(
            """
                DELETE FROM metrics.test_to_code_mapping tcm
                WHERE EXISTS (SELECT 1 
                    FROM metrics.test_definitions td
                    WHERE td.updated_at_day < ?
                        AND td.group_id = tcm.group_id
                        AND td.test_definition_id = tcm.test_definition_id
                )
                """.trimIndent(), timestamp
        )
        executeUpdate("DELETE FROM metrics.test_definitions WHERE updated_at_day < ?", timestamp)
    }

    override suspend fun deleteAllDailyDataCreatedBefore(groupId: String, timestamp: Instant) = transaction {
        val timestamp = Timestamp.from(timestamp)
        executeUpdate("DELETE FROM metrics.method_daily_coverage WHERE created_at_day < ?", timestamp)
    }

    override suspend fun deleteAllBuildDataByBuildId(
        groupId: String,
        appId: String,
        buildId: String
    ) = transaction {
        executeUpdate(
            "DELETE FROM metrics.build_method_test_definition_coverage WHERE group_id = ? AND app_id = ? AND build_id = ?",
            groupId,
            appId,
            buildId
        )
        executeUpdate(
            "DELETE FROM metrics.build_method_test_session_coverage WHERE group_id = ? AND app_id = ? AND build_id = ?",
            groupId,
            appId,
            buildId
        )
        executeUpdate(
            "DELETE FROM metrics.build_method_coverage WHERE group_id = ? AND app_id = ? AND build_id = ?",
            groupId,
            appId,
            buildId
        )
        // deleting from metrics.method_daily_coverage is impossible because the table does not reference build_id
        // deleting from metrics.test_to_code_mapping is impossible because the table does not reference build_id
        executeUpdate(
            "DELETE FROM metrics.test_session_builds WHERE group_id = ? AND app_id = ? AND build_id = ?",
            groupId,
            appId,
            buildId
        )
        executeUpdate(
            "DELETE FROM metrics.build_methods WHERE group_id = ? AND app_id = ? AND build_id = ?",
            groupId,
            appId,
            buildId
        )
        executeUpdate(
            "DELETE FROM metrics.builds WHERE group_id = ? AND app_id = ? AND build_id = ?",
            groupId,
            appId,
            buildId
        )
    }

    override suspend fun deleteAllTestDataByTestSessionId(
        groupId: String,
        testSessionId: String
    ) = transaction {
        executeUpdate(
            "DELETE FROM metrics.build_method_test_definition_coverage WHERE group_id = ? AND test_session_id = ?",
            groupId,
            testSessionId
        )
        executeUpdate(
            "DELETE FROM metrics.build_method_test_session_coverage WHERE group_id = ? AND test_session_id = ?",
            groupId,
            testSessionId
        )
        // deleting from metrics.build_method_coverage is impossible because the table does not reference test_session_id
        // deleting from metrics.method_daily_coverage is impossible because the table does not linked to test_session_id
        // deleting from metrics.test_to_code_mapping is impossible because the table does not reference test_session_id
        executeUpdate(
            "DELETE FROM metrics.test_launches WHERE group_id = ? AND test_session_id = ?",
            groupId,
            testSessionId
        )
        executeUpdate(
            "DELETE FROM metrics.test_session_builds WHERE group_id = ? AND test_session_id = ?",
            groupId,
            testSessionId
        )
        executeUpdate(
            "DELETE FROM metrics.test_sessions WHERE group_id = ? AND test_session_id = ?",
            groupId,
            testSessionId
        )
    }

    override suspend fun deleteAllOrphanReferences() = transaction {
        executeUpdate(
            """
                DELETE FROM metrics.methods m
                WHERE NOT EXISTS (SELECT 1 
                    FROM metrics.build_methods bm 
                    WHERE bm.group_id = m.group_id 
                        AND bm.app_id = m.app_id
                        AND bm.method_id = m.method_id
                )
                """.trimIndent()
        )
        executeUpdate(
            """
                DELETE FROM metrics.method_daily_coverage c
                WHERE NOT EXISTS (SELECT 1
                    FROM metrics.methods m
                    WHERE m.group_id = c.group_id
                        AND m.app_id = c.app_id
                        AND m.method_id = c.method_id
                )
                """.trimIndent()
        )
        executeUpdate(
            """
                DELETE FROM metrics.test_to_code_mapping tcm
                WHERE NOT EXISTS (SELECT 1
                    FROM metrics.methods m
                    WHERE m.group_id = tcm.group_id
                        AND m.app_id = tcm.app_id
                        AND m.signature = tcm.signature
                )
                """.trimIndent()
        )
    }
}
