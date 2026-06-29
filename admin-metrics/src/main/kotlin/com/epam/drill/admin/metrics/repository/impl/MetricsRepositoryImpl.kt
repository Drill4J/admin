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
import com.epam.drill.admin.metrics.config.SqlBuilder
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

    override suspend fun getGroups(): List<String> = transaction {
        executeQueryReturnMap(
            """
            SELECT DISTINCT group_id
            FROM metrics.builds
            ORDER BY group_id
            """.trimIndent()
        ).map { it["group_id"] as String }
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
        branches: List<String>, envIds: List<String>,
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
            appendOptional(" AND b.branch = ANY(?)", branches)
            appendOptional(" AND b.app_env_ids && ?::varchar[]", envIds)
            append(" ORDER BY COALESCE(b.committed_at, b.created_at) DESC ")
            appendOptional(" OFFSET ?", offset)
            appendOptional(" LIMIT ?", limit)
        }
    }

    override suspend fun getAppBranches(groupId: String, appId: String): List<String> = transaction {
        executeQueryReturnMap(
            """
            SELECT DISTINCT branch
            FROM metrics.builds
            WHERE group_id = ? AND app_id = ?
              AND branch IS NOT NULL AND branch <> ''
            ORDER BY branch
            """.trimIndent(),
            groupId, appId
        ).map { it["branch"] as String }
    }

    override suspend fun getAppEnvIds(groupId: String, appId: String): List<String> = transaction {
        executeQueryReturnMap(
            """
            SELECT DISTINCT env_id
            FROM metrics.builds b
            CROSS JOIN LATERAL unnest(b.app_env_ids) AS env_id
            WHERE b.group_id = ? AND b.app_id = ?
            ORDER BY env_id
            """.trimIndent(),
            groupId, appId
        ).map { it["env_id"] as String }
    }

    override suspend fun getAppTestTags(groupId: String, appId: String): List<String> = transaction {
        executeQueryReturnMap(
            """
            SELECT DISTINCT tag AS test_tag
            FROM metrics.test_definitions td
            JOIN metrics.test_launches tl
                ON tl.group_id = td.group_id
                AND tl.test_definition_id = td.test_definition_id
            JOIN metrics.test_session_builds tsb
                ON tsb.group_id = tl.group_id
                AND tsb.test_session_id = tl.test_session_id
            JOIN metrics.builds b
                ON b.group_id = tsb.group_id
                AND b.build_id = tsb.build_id
            CROSS JOIN LATERAL unnest(td.test_tags) AS tag
            WHERE b.group_id = ? AND b.app_id = ?
              AND tag IS NOT NULL AND tag <> ''
            ORDER BY tag
            """.trimIndent(),
            groupId, appId
        ).map { it["test_tag"] as String }
    }

    override suspend fun getBuildDetail(buildId: String): Map<String, Any?>? = transaction {
        executeQueryReturnMap(
            """
            SELECT
                group_id,
                app_id,
                build_id,
                version_id,
                build_version,
                branch,
                commit_sha,
                commit_author,
                commit_message,
                committed_at,
                app_env_ids,
                total_classes,
                total_methods,
                total_probes
            FROM metrics.builds_with_statistics
            WHERE build_id = ?
            """.trimIndent(),
            buildId
        ).firstOrNull()
    }

    override suspend fun getBuildCoverageSummary(
        buildId: String,
        baselineBuildId: String?,
        envIds: List<String>,
        branches: List<String>,
        testTags: List<String>,
    ): Map<String, Any?>? = transaction {
        executeQueryReturnMap {
            append(
                """
                SELECT
                    total_probes,
                    isolated_covered_probes,
                    aggregated_covered_probes,
                    total_methods,
                    isolated_tested_methods,
                    aggregated_tested_methods
                FROM metrics.get_builds_with_coverage(
                    input_build_id => ?
                """.trimIndent(), buildId
            )
            appendOptional(", input_baseline_build_id => ?", baselineBuildId)
            appendCoverageFilterParams(testTags, envIds, branches)
            append("\n)")
        }.firstOrNull()
    }

    override suspend fun getChangesSummary(
        buildId: String,
        baselineBuildId: String,
    ): Map<String, Any?> = transaction {
        executeQueryReturnMap(
            """
            SELECT
                COUNT(CASE WHEN change_type = 'modified' THEN 1 END) AS modified_methods,
                COUNT(CASE WHEN change_type = 'new' THEN 1 END) AS new_methods,
                COUNT(CASE WHEN change_type = 'deleted' THEN 1 END) AS deleted_methods
            FROM metrics.get_changes(
                input_build_id => ?,
                input_baseline_build_id => ?,
                include_deleted => true
            )
            """.trimIndent(),
            buildId,
            baselineBuildId
        ).firstOrNull() ?: mapOf(
            "modified_methods" to 0,
            "new_methods" to 0,
            "deleted_methods" to 0
        )
    }

    override suspend fun getSimilarBuilds(buildId: String): List<Map<String, Any?>> = transaction {
        executeQueryReturnMap(
            """
            SELECT
                sb.build_id,
                b.version_id,
                b.build_version,
                b.branch,
                sb.identity_ratio,
                sb.target_equal_methods,
                sb.target_total_methods
            FROM metrics.get_similar_builds(input_build_id => ?) sb
            JOIN metrics.builds b
                ON b.group_id = sb.group_id
                AND b.app_id = sb.app_id
                AND b.build_id = sb.build_id
            ORDER BY sb.identity_ratio DESC, b.committed_at DESC NULLS LAST
            """.trimIndent(),
            buildId
        )
    }

    override suspend fun getBuildTestSessionStats(buildId: String): Map<String, Any?> = transaction {
        executeQueryReturnMap(
            """
            SELECT
                COUNT(DISTINCT tsb.test_session_id) AS session_count,
                COUNT(tl.test_launch_id) AS test_run_count
            FROM metrics.test_session_builds tsb
            LEFT JOIN metrics.test_launches tl
                ON tl.group_id = tsb.group_id
                AND tl.test_session_id = tsb.test_session_id
            WHERE tsb.build_id = ?
            """.trimIndent(),
            buildId
        ).firstOrNull() ?: mapOf(
            "session_count" to 0,
            "test_run_count" to 0
        )
    }

    override suspend fun getBuildsCount(
        groupId: String, appId: String,
        branches: List<String>, envIds: List<String>
    ): Long = transaction {
        val result = executeQueryReturnMap {
            append(
                """
            SELECT COUNT(*) AS cnt
            FROM metrics.builds b
            WHERE b.group_id = ? AND b.app_id = ?
            """.trimIndent(), groupId, appId
            )
            appendOptional(" AND b.branch = ANY(?)", branches)
            appendOptional(" AND b.app_env_ids && ?::varchar[]", envIds)
        }
        (result[0]["cnt"] as? Number)?.toLong() ?: 0
    }

    override suspend fun getMethodsWithCoverage(
        buildId: String,
        coverageTestTags: List<String>,
        coverageAppEnvIds: List<String>,
        coverageBranches: List<String>,
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
            appendCoverageFilterParams(coverageTestTags, coverageAppEnvIds, coverageBranches)
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

    override suspend fun getMethodsWithCoverageByTestSession(
        buildId: String,
        testSessionId: String,
        testTags: List<String>,
        packageNamePattern: String?,
        methodSignaturePattern: String?,
        coverageAppEnvIds: List<String>,
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
                    covered_probes AS isolated_covered_probes,
                    covered_probes AS aggregated_covered_probes,                    
                    probes_coverage_ratio AS isolated_probes_coverage_ratio,
                    probes_coverage_ratio AS aggregated_probes_coverage_ratio                    
                FROM metrics.get_methods_with_coverage_by_test_session(
                    input_build_id => ?,
                    input_test_session_id => ?
                """.trimIndent(), buildId, testSessionId
            )
            appendOptional(", input_coverage_test_tags => ?", testTags)
            appendOptional(", input_package_name_pattern => ?", packageNamePattern) { "$it%" }
            appendOptional(", input_signature_pattern => ?", methodSignaturePattern)
            appendOptional(", input_coverage_app_env_ids => ?", coverageAppEnvIds)
            append(
                """
                ) 
                ORDER BY signature    
                """.trimIndent()
            )
        }
    }

    override suspend fun getMethodsWithCoverageByTestDefinition(
        buildId: String,
        testSessionId: String,
        testDefinitionId: String,
        packageNamePattern: String?,
        methodSignaturePattern: String?,
        coverageAppEnvIds: List<String>,
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
                    covered_probes AS isolated_covered_probes,
                    covered_probes AS aggregated_covered_probes,                    
                    probes_coverage_ratio AS isolated_probes_coverage_ratio,
                    probes_coverage_ratio AS aggregated_probes_coverage_ratio                    
                FROM metrics.get_methods_with_coverage_by_test_definition(
                    input_build_id => ?,
                    input_test_session_id => ?,
                    input_test_definition_id => ?
                """.trimIndent(), buildId, testSessionId, testDefinitionId
            )
            appendOptional(", input_package_name_pattern => ?", packageNamePattern) { "$it%" }
            appendOptional(", input_signature_pattern => ?", methodSignaturePattern)
            appendOptional(", input_coverage_app_env_ids => ?", coverageAppEnvIds)
            append(
                """
                ) 
                ORDER BY signature    
                """.trimIndent()
            )
        }
    }


    override suspend fun getChangesWithCoverage(
        buildId: String,
        baselineBuildId: String?,
        coverageTestTags: List<String>,
        coverageAppEnvIds: List<String>,
        coverageBranches: List<String>,
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
            appendCoverageFilterParams(coverageTestTags, coverageAppEnvIds, coverageBranches)
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

    override suspend fun getPackageCoverage(
        buildId: String,
        coverageTestTags: List<String>,
        coverageAppEnvIds: List<String>,
        coverageBranches: List<String>,
    ): List<Map<String, Any?>> = transaction {
        executeQueryReturnMap {
            append(
                """
                SELECT
                    package_name,
                    COUNT(*)::INT AS methods_count,
                    COUNT(*) FILTER (WHERE isolated_covered_probes > 0)::INT AS covered_methods,
                    (COUNT(*) - COUNT(*) FILTER (WHERE isolated_covered_probes > 0))::INT AS missed_methods,
                    COALESCE(SUM(probes_count), 0)::INT AS probes_count,
                    COALESCE(SUM(isolated_covered_probes), 0)::INT AS covered_probes,
                    (COALESCE(SUM(probes_count), 0) - COALESCE(SUM(isolated_covered_probes), 0))::INT AS missed_probes
                FROM (
                    SELECT
                        $PACKAGE_NAME_SQL AS package_name,
                        probes_count,
                        isolated_covered_probes
                    FROM metrics.get_methods_with_coverage(
                        input_build_id => ?
                """.trimIndent(), buildId
            )
            appendCoverageFilterParams(coverageTestTags, coverageAppEnvIds, coverageBranches)
            append(
                """
                    )
                ) methods_with_package
                GROUP BY package_name
                ORDER BY package_name
                """.trimIndent()
            )
        }
    }

    override suspend fun getClassCoverage(
        buildId: String,
        packageName: String?,
        coverageTestTags: List<String>,
        coverageAppEnvIds: List<String>,
        coverageBranches: List<String>,
        sortBy: String?,
        sortOrder: SortOrder?,
        offset: Int?,
        limit: Int?,
    ): List<Map<String, Any?>> = transaction {
        val sortDirection = sortOrder?.name ?: "ASC"
        val orderBy = when (sortBy) {
            "methods_coverage_ratio" -> """
                CASE
                    WHEN methods_count > 0 THEN covered_methods::DOUBLE PRECISION / methods_count::DOUBLE PRECISION
                    ELSE 0
                END $sortDirection, class_name ASC
            """.trimIndent()
            "methods_count" -> "methods_count $sortDirection, class_name ASC"
            "covered_methods" -> "covered_methods $sortDirection, class_name ASC"
            "probes_coverage_ratio" -> """
                CASE
                    WHEN probes_count > 0 THEN covered_probes::DOUBLE PRECISION / probes_count::DOUBLE PRECISION
                    ELSE 0
                END $sortDirection, class_name ASC
            """.trimIndent()
            "probes_count" -> "probes_count $sortDirection, class_name ASC"
            "covered_probes" -> "covered_probes $sortDirection, class_name ASC"
            else -> "class_name ASC"
        }

        executeQueryReturnMap {
            append(
                """
                SELECT *
                FROM (
                    SELECT
                        class_name,
                        COUNT(*)::INT AS methods_count,
                        COUNT(*) FILTER (WHERE isolated_covered_probes > 0)::INT AS covered_methods,
                        (COUNT(*) - COUNT(*) FILTER (WHERE isolated_covered_probes > 0))::INT AS missed_methods,
                        COALESCE(SUM(probes_count), 0)::INT AS probes_count,
                        COALESCE(SUM(isolated_covered_probes), 0)::INT AS covered_probes,
                        (COALESCE(SUM(probes_count), 0) - COALESCE(SUM(isolated_covered_probes), 0))::INT AS missed_probes
                    FROM metrics.get_methods_with_coverage(
                        input_build_id => ?
                """.trimIndent(), buildId
            )
            appendOptional(", input_package_name_pattern => ?", packageName) { "$it%" }
            appendCoverageFilterParams(coverageTestTags, coverageAppEnvIds, coverageBranches)
            append(
                """
                    )
                    GROUP BY class_name
                ) AS class_coverage
                ORDER BY $orderBy
                """.trimIndent()
            )
            appendOptional(" OFFSET ?", offset)
            appendOptional(" LIMIT ?", limit)
        }
    }

    override suspend fun getClassCoverageCount(
        buildId: String,
        packageName: String?,
        coverageTestTags: List<String>,
        coverageAppEnvIds: List<String>,
        coverageBranches: List<String>,
    ): Long = transaction {
        val result = executeQueryReturnMap {
            append(
                """
                SELECT COUNT(*) AS cnt
                FROM (
                    SELECT class_name
                    FROM metrics.get_methods_with_coverage(
                        input_build_id => ?
                """.trimIndent(), buildId
            )
            appendOptional(", input_package_name_pattern => ?", packageName) { "$it%" }
            appendCoverageFilterParams(coverageTestTags, coverageAppEnvIds, coverageBranches)
            append(
                """
                    )
                    GROUP BY class_name
                ) AS class_coverage
                """.trimIndent()
            )
        }
        (result.firstOrNull()?.get("cnt") as? Number)?.toLong() ?: 0
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
            append(
                """
                    ImpactedTestsWithResults AS (
                        SELECT  
                            COUNT(*) AS impacted_tests,
		                    SUM(CASE WHEN test_result = 'PASSED' THEN 1 ELSE 0 END) AS passed_impacted_tests,
		                    SUM(CASE WHEN test_result = 'FAILED' THEN 1 ELSE 0 END) AS failed_impacted_tests
                        FROM ImpactedTests it	
                        LEFT JOIN TestLaunches tl ON tl.test_definition_id = it.test_definition_id	
                    ) 
            """.trimIndent()
            )
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

    override suspend fun getImpactedTestsCount(
        targetBuildId: String,
        baselineBuildId: String,
    ): Long = transaction {
        val result = executeQueryReturnMap(
            """
            SELECT COUNT(*) AS cnt
            FROM metrics.get_impacted_tests_v2(
                input_build_id => ?,
                input_baseline_build_id => ?
            )
            """.trimIndent(),
            targetBuildId,
            baselineBuildId
        )
        (result.firstOrNull()?.get("cnt") as? Number)?.toLong() ?: 0
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

    override suspend fun getImpactedMethodsCount(
        targetBuildId: String,
        baselineBuildId: String,
    ): Long = transaction {
        val result = executeQueryReturnMap(
            """
            SELECT COUNT(*) AS cnt
            FROM metrics.get_impacted_methods_v2(
                input_build_id => ?,
                input_baseline_build_id => ?
            )
            """.trimIndent(),
            targetBuildId,
            baselineBuildId
        )
        (result.firstOrNull()?.get("cnt") as? Number)?.toLong() ?: 0
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

    override suspend fun deleteAllOrphanReferences(groupId: String, timestamp: Instant) = transaction {
        val timestamp = Timestamp.from(timestamp)
        executeUpdate(
            """
                DELETE FROM metrics.methods m
                WHERE m.group_id = ?
                    AND m.created_at_day < ?
                    AND NOT EXISTS (SELECT 1 
                    FROM metrics.build_methods bm 
                    WHERE bm.group_id = m.group_id 
                        AND bm.app_id = m.app_id
                        AND bm.method_id = m.method_id
                )
                """.trimIndent(),
            groupId, timestamp
        )
        executeUpdate(
            """
                DELETE FROM metrics.method_daily_coverage c
                WHERE c.group_id = ?
                    AND c.created_at_day < ?
                    AND NOT EXISTS (SELECT 1
                    FROM metrics.methods m
                    WHERE m.group_id = c.group_id
                        AND m.app_id = c.app_id
                        AND m.method_id = c.method_id
                )
                """.trimIndent(),
            groupId, timestamp
        )
        executeUpdate(
            """
                DELETE FROM metrics.test_to_code_mapping tcm
                WHERE tcm.group_id = ?
                    AND tcm.created_at_day < ?
                    AND NOT EXISTS (SELECT 1
                    FROM metrics.methods m
                    WHERE m.group_id = tcm.group_id
                        AND m.app_id = tcm.app_id
                        AND m.signature = tcm.signature
                )
                """.trimIndent(),
            groupId, timestamp
        )
    }
}

private fun SqlBuilder.appendCoverageFilterParams(
    coverageTestTags: List<String>,
    coverageAppEnvIds: List<String>,
    coverageBranches: List<String>,
) {
    appendOptional(", input_coverage_test_tags => ?", coverageTestTags)
    appendOptional(", input_coverage_app_env_ids => ?", coverageAppEnvIds)
    appendOptional(", input_coverage_branches => ?", coverageBranches)
}

// Drill stores class names with "/" package separators (same as treemap builder).
private const val PACKAGE_NAME_SQL = """
    CASE
        WHEN POSITION('/' IN class_name) > 0 THEN
            REVERSE(SUBSTRING(REVERSE(class_name) FROM POSITION('/' IN REVERSE(class_name)) + 1))
        ELSE ''
    END
"""
