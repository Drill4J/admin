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
import com.epam.drill.admin.metrics.models.BuildSortField
import com.epam.drill.admin.metrics.models.SortOrder
import com.epam.drill.admin.metrics.repository.MetricsRepository
import com.epam.drill.admin.metrics.util.sqlSortDirection
import com.epam.drill.admin.metrics.views.TestImpactStatus
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
        commitSha: String?,
        buildVersion: String?,
        sortBy: BuildSortField?, sortOrder: SortOrder?,
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
            appendOptional(" AND b.commit_sha = ?", commitSha)
            appendOptional(" AND b.build_version = ?", buildVersion)
            val direction = sqlSortDirection(sortOrder, default = SortOrder.DESC)
            when (sortBy ?: BuildSortField.COMMIT_DATE) {
                BuildSortField.COMMIT_DATE -> "COALESCE(b.committed_at, b.created_at) $direction"
                BuildSortField.BUILD_VERSION -> (1..3).joinToString(separator = ", ") {
                    "SPLIT_PART(b.build_version, '.', $it) $direction"
                }
            }.let {
                append(" ORDER BY $it")
            }
            appendOptional(" OFFSET ?", offset)
            appendOptional(" LIMIT ?", limit)
        }
    }

    override suspend fun getAppBranches(
        groupId: String,
        appId: String,
        query: String?,
        offset: Int?,
        limit: Int?,
    ): List<String> = transaction {
        executeQueryReturnMap {
            append(
                """
            SELECT DISTINCT branch
            FROM metrics.builds
            WHERE group_id = ? AND app_id = ?
              AND branch IS NOT NULL AND branch <> ''
                """.trimIndent(),
                groupId, appId
            )
            appendOptional(" AND branch ILIKE ?", query, transform = { "%$it%" })
            append(" ORDER BY branch ")
            appendOptional(" OFFSET ?", offset)
            appendOptional(" LIMIT ?", limit)
        }.map { it["branch"] as String }
    }

    override suspend fun getAppBranchesCount(groupId: String, appId: String, query: String?): Long = transaction {
        executeQueryReturnMap {
            append(
                """
            SELECT COUNT(DISTINCT branch) AS total
            FROM metrics.builds
            WHERE group_id = ? AND app_id = ?
              AND branch IS NOT NULL AND branch <> ''
                """.trimIndent(),
                groupId, appId
            )
            appendOptional(" AND branch ILIKE ?", query, transform = { "%$it%" })
        }.firstOrNull()?.get("total").let(::totalAsLong)
    }

    override suspend fun getAppEnvIds(
        groupId: String,
        appId: String,
        query: String?,
        offset: Int?,
        limit: Int?,
    ): List<String> = transaction {
        executeQueryReturnMap {
            append(
                """
            SELECT DISTINCT env_id
            FROM metrics.builds b
            CROSS JOIN LATERAL unnest(b.app_env_ids) AS env_id
            WHERE b.group_id = ? AND b.app_id = ?
                """.trimIndent(),
                groupId, appId
            )
            appendOptional(" AND env_id ILIKE ?", query, transform = { "%$it%" })
            append(" ORDER BY env_id ")
            appendOptional(" OFFSET ?", offset)
            appendOptional(" LIMIT ?", limit)
        }.map { it["env_id"] as String }
    }

    override suspend fun getAppEnvIdsCount(groupId: String, appId: String, query: String?): Long = transaction {
        executeQueryReturnMap {
            append(
                """
            SELECT COUNT(DISTINCT env_id) AS total
            FROM metrics.builds b
            CROSS JOIN LATERAL unnest(b.app_env_ids) AS env_id
            WHERE b.group_id = ? AND b.app_id = ?
                """.trimIndent(),
                groupId, appId
            )
            appendOptional(" AND env_id ILIKE ?", query, transform = { "%$it%" })
        }.firstOrNull()?.get("total").let(::totalAsLong)
    }

    override suspend fun getAppTestTags(
        groupId: String,
        appId: String,
        query: String?,
        offset: Int?,
        limit: Int?,
    ): List<String> = transaction {
        executeQueryReturnMap {
            append(
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
                """.trimIndent(),
                groupId, appId
            )
            appendOptional(" AND tag ILIKE ?", query, transform = { "%$it%" })
            append(" ORDER BY tag ")
            appendOptional(" OFFSET ?", offset)
            appendOptional(" LIMIT ?", limit)
        }.map { it["test_tag"] as String }
    }

    override suspend fun getAppTestTagsCount(groupId: String, appId: String, query: String?): Long = transaction {
        executeQueryReturnMap {
            append(
                """
            SELECT COUNT(DISTINCT tag) AS total
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
                """.trimIndent(),
                groupId, appId
            )
            appendOptional(" AND tag ILIKE ?", query, transform = { "%$it%" })
        }.firstOrNull()?.get("total").let(::totalAsLong)
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

    override suspend fun getAppCoverageTrends(
        groupId: String,
        appId: String,
        branches: List<String>,
        envIds: List<String>,
        testTags: List<String>,
        size: Int,
    ): List<Map<String, Any?>> = transaction {
        executeQueryReturnMap {
            append(
                """
                WITH builds AS (
                    SELECT
                        b.group_id,
                        b.app_id,
                        b.build_id,
                        b.build_version AS build_label,
                        COALESCE(b.committed_at, b.created_at) AS build_date,
                        b.committed_at,
                        b.created_at
                    FROM metrics.builds b
                    WHERE b.group_id = ?
                      AND b.app_id = ?
                """.trimIndent(),
                groupId,
                appId,
            )
            appendOptional(" AND b.branch = ANY(?)", branches)
            appendOptional(" AND b.app_env_ids && ?::varchar[]", envIds)
            append(
                """
                    ORDER BY b.created_at DESC
                    LIMIT ?
                )
                SELECT
                    b.build_id,
                    b.build_label,
                    b.build_date,
                    COALESCE(c.isolated_probes_coverage_ratio, 0) AS isolated_probes_coverage_ratio,
                    COALESCE(c.aggregated_probes_coverage_ratio, 0) AS aggregated_probes_coverage_ratio
                FROM builds b
                LEFT JOIN metrics.get_builds_with_coverage(
                    input_build_ids => ARRAY(SELECT build_id FROM builds)
                """.trimIndent(),
                size,
            )
            appendCoverageFilterParams(testTags, envIds, branches)
            append(
                """
                ) c
                    ON c.group_id = b.group_id
                    AND c.app_id = b.app_id
                    AND c.build_id = b.build_id
                ORDER BY b.build_date ASC, b.created_at ASC
                """.trimIndent()
            )
        }
    }

    override suspend fun getAppChangesTrends(
        groupId: String,
        appId: String,
        baselineBuildId: String,
        branches: List<String>,
        envIds: List<String>,
        testTags: List<String>,
        size: Int,
    ): List<Map<String, Any?>> = transaction {
        executeQueryReturnMap {
            append(
                """
                WITH baseline_info AS (
                    SELECT COALESCE(b.committed_at, b.created_at) AS baseline_at
                    FROM metrics.builds b
                    WHERE b.build_id = ?
                ),
                builds AS (
                    SELECT
                        b.group_id,
                        b.app_id,
                        b.build_id,
                        b.build_version AS build_label,
                        COALESCE(b.committed_at, b.created_at) AS build_date,
                        b.committed_at,
                        b.created_at
                    FROM metrics.builds b
                    CROSS JOIN baseline_info bi
                    WHERE b.group_id = ?
                      AND b.app_id = ?
                      AND COALESCE(b.committed_at, b.created_at) >= bi.baseline_at
                """.trimIndent(),
                baselineBuildId,
                groupId,
                appId,
            )
            appendOptional(" AND b.branch = ANY(?)", branches)
            appendOptional(" AND b.app_env_ids && ?::varchar[]", envIds)
            append(
                """
                    ORDER BY COALESCE(b.committed_at, b.created_at) ASC, b.created_at ASC
                    LIMIT ?
                )
                SELECT
                    b.build_id,
                    b.build_label,
                    b.build_date,
                    COALESCE(c.total_probes, 0) AS total_probes,
                    COALESCE(c.isolated_covered_probes, 0) AS isolated_covered_probes,
                    COALESCE(c.aggregated_covered_probes, 0) AS aggregated_covered_probes,
                    COALESCE(c.total_methods, 0) AS total_methods,
                    COALESCE(c.isolated_tested_methods, 0) AS isolated_tested_methods,
                    COALESCE(c.aggregated_tested_methods, 0) AS aggregated_tested_methods
                FROM builds b
                LEFT JOIN metrics.get_builds_with_coverage(
                    input_build_ids => ARRAY(SELECT build_id FROM builds),
                    input_baseline_build_id => ?
                """.trimIndent(),
                size,
                baselineBuildId,
            )
            appendCoverageFilterParams(testTags, envIds, branches)
            append(
                """
                ) c
                    ON c.group_id = b.group_id
                    AND c.app_id = b.app_id
                    AND c.build_id = b.build_id
                ORDER BY b.build_date ASC, b.created_at ASC
                """.trimIndent()
            )
        }
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

    override suspend fun getGroupTestSessions(
        groupId: String,
        testTaskIds: List<String>,
        createdBys: List<String>,
        results: List<String>,
        sortBy: String?,
        sortOrder: SortOrder?,
        offset: Int?,
        limit: Int?,
    ): List<Map<String, Any?>> = transaction {
        val orderBy = resolveTestSessionOrderBy(sortBy, sortOrder)
        executeQueryReturnMap {
            append(
                """
            SELECT
                tss.group_id,
                tss.test_session_id,
                tss.test_task_id,
                tss.session_started_at,
                tss.created_by,
                tss.test_definitions,
                tss.test_launches,
                tss.result,
                tss.test_duration,
                metrics.format_duration(tss.test_duration::bigint) AS test_duration_formatted,
                tss.failed,
                tss.passed,
                tss.skipped,
                tss.smart_skipped,
                tss.success,
                tss.success_rate,
                tss.time_saved,
                metrics.format_duration_rounded(tss.time_saved::bigint) AS time_saved_formatted
            FROM metrics.test_sessions_with_statistics tss
            WHERE tss.group_id = ?
                AND EXISTS (
                    SELECT 1
                    FROM metrics.test_session_builds tsb
                    WHERE tsb.group_id = tss.group_id
                        AND tsb.test_session_id = tss.test_session_id
                )
                """.trimIndent(),
                groupId,
            )
            appendTestSessionFilters(testTaskIds, createdBys, results)
            append(" ORDER BY $orderBy ")
            appendOptional(" OFFSET ?", offset)
            appendOptional(" LIMIT ?", limit)
        }
    }

    override suspend fun getGroupTestSessionsCount(
        groupId: String,
        testTaskIds: List<String>,
        createdBys: List<String>,
        results: List<String>,
    ): Long = transaction {
        executeQueryReturnMap {
            append(
                """
            SELECT COUNT(*) AS total
            FROM metrics.test_sessions_with_statistics tss
            WHERE tss.group_id = ?
                AND EXISTS (
                    SELECT 1
                    FROM metrics.test_session_builds tsb
                    WHERE tsb.group_id = tss.group_id
                        AND tsb.test_session_id = tss.test_session_id
                )
                """.trimIndent(),
                groupId,
            )
            appendTestSessionFilters(testTaskIds, createdBys, results)
        }.firstOrNull()?.get("total")?.let {
            when (it) {
                is Long -> it
                is Number -> it.toLong()
                else -> 0L
            }
        } ?: 0L
    }

    override suspend fun getBuildTestSessions(
        groupId: String,
        buildId: String,
        testTaskIds: List<String>,
        createdBys: List<String>,
        results: List<String>,
        sortBy: String?,
        sortOrder: SortOrder?,
        offset: Int?,
        limit: Int?,
    ): List<Map<String, Any?>> = transaction {
        val orderBy = resolveTestSessionOrderBy(sortBy, sortOrder)
        executeQueryReturnMap {
            append(
                """
            SELECT
                tsb.group_id,
                tsb.app_id,
                tsb.build_id,
                tss.test_session_id,
                tss.test_task_id,
                tss.session_started_at,
                tss.created_by,
                tss.test_definitions,
                tss.test_launches,
                tss.result,
                tss.test_duration,
                metrics.format_duration(tss.test_duration::bigint) AS test_duration_formatted,
                tss.failed,
                tss.passed,
                tss.skipped,
                tss.smart_skipped,
                tss.success,
                tss.success_rate,
                tss.time_saved,
                metrics.format_duration_rounded(tss.time_saved::bigint) AS time_saved_formatted
            FROM metrics.test_session_builds tsb
            JOIN metrics.test_sessions_with_statistics tss
                ON tss.group_id = tsb.group_id
                AND tss.test_session_id = tsb.test_session_id
            WHERE tsb.group_id = ?
                AND tsb.build_id = ?
                """.trimIndent(),
                groupId,
                buildId,
            )
            appendTestSessionFilters(testTaskIds, createdBys, results)
            append(" ORDER BY $orderBy ")
            appendOptional(" OFFSET ?", offset)
            appendOptional(" LIMIT ?", limit)
        }
    }

    override suspend fun getBuildTestSessionsCount(
        groupId: String,
        buildId: String,
        testTaskIds: List<String>,
        createdBys: List<String>,
        results: List<String>,
    ): Long = transaction {
        executeQueryReturnMap {
            append(
                """
            SELECT COUNT(*) AS total
            FROM metrics.test_session_builds tsb
            JOIN metrics.test_sessions_with_statistics tss
                ON tss.group_id = tsb.group_id
                AND tss.test_session_id = tsb.test_session_id
            WHERE tsb.group_id = ?
                AND tsb.build_id = ?
                """.trimIndent(),
                groupId,
                buildId,
            )
            appendTestSessionFilters(testTaskIds, createdBys, results)
        }.firstOrNull()?.get("total")?.let {
            when (it) {
                is Long -> it
                is Number -> it.toLong()
                else -> 0L
            }
        } ?: 0L
    }

    override suspend fun getTestSessionBuilds(
        groupId: String,
        testSessionId: String,
        offset: Int?,
        limit: Int?,
    ): List<Map<String, Any?>> = transaction {
        executeQueryReturnMap {
            append(
                """
            SELECT
                tsb.app_id,
                tsb.build_id,
                bws.build_version,
                bws.branch,
                COALESCE(cov.covered_probes, 0) AS covered_probes,
                COALESCE(cov.total_probes, 0) AS total_probes,
                COALESCE(cov.tested_methods, 0) AS covered_methods,
                COALESCE(cov.total_methods, 0) AS total_methods
            FROM metrics.test_session_builds tsb
            LEFT JOIN metrics.builds_with_statistics bws
                ON bws.group_id = tsb.group_id
                AND bws.app_id = tsb.app_id
                AND bws.build_id = tsb.build_id
            LEFT JOIN metrics.get_builds_with_coverage_by_test_session(
                input_build_id => tsb.build_id,
                input_test_session_id => tsb.test_session_id
            ) cov ON TRUE
            WHERE tsb.group_id = ?
                AND tsb.test_session_id = ?
            ORDER BY tsb.app_id, bws.build_version NULLS LAST, tsb.build_id
                """.trimIndent(),
                groupId,
                testSessionId,
            )
            appendOptional(" OFFSET ?", offset)
            appendOptional(" LIMIT ?", limit)
        }
    }

    override suspend fun getTestSessionBuildsCount(
        groupId: String,
        testSessionId: String,
    ): Long = transaction {
        executeQueryReturnMap(
            """
            SELECT COUNT(*) AS total
            FROM metrics.test_session_builds tsb
            WHERE tsb.group_id = ?
                AND tsb.test_session_id = ?
            """.trimIndent(),
            groupId,
            testSessionId,
        ).firstOrNull()?.get("total")?.let {
            when (it) {
                is Long -> it
                is Number -> it.toLong()
                else -> 0L
            }
        } ?: 0L
    }

    override suspend fun getTestSessionTestTaskIds(groupId: String, buildId: String?): List<String> =
        getTestSessionDistinctValues(groupId, buildId, TestSessionDistinctColumn.TEST_TASK_ID)

    override suspend fun getTestSessionCreatedBys(groupId: String, buildId: String?): List<String> =
        getTestSessionDistinctValues(groupId, buildId, TestSessionDistinctColumn.CREATED_BY)

    override suspend fun getTestSessionResults(groupId: String, buildId: String?): List<String> =
        getTestSessionDistinctValues(groupId, buildId, TestSessionDistinctColumn.RESULT)

    override suspend fun getTestSessionFilterValues(
        groupId: String,
        buildId: String?,
        field: String,
        query: String?,
        offset: Int?,
        limit: Int?,
    ): List<String> = getTestSessionDistinctValues(
        groupId = groupId,
        buildId = buildId,
        column = testSessionFilterField(field),
        query = query,
        offset = offset,
        limit = limit,
    )

    override suspend fun getTestSessionFilterValuesCount(
        groupId: String,
        buildId: String?,
        field: String,
        query: String?,
    ): Long = getTestSessionDistinctValuesCount(
        groupId = groupId,
        buildId = buildId,
        column = testSessionFilterField(field),
        query = query,
    )

    override suspend fun testSessionExists(groupId: String, testSessionId: String): Boolean = transaction {
        executeQueryReturnMap(
            """
            SELECT 1
            FROM metrics.test_sessions
            WHERE group_id = ? AND test_session_id = ?
            LIMIT 1
            """.trimIndent(),
            groupId,
            testSessionId
        ).isNotEmpty()
    }

    override suspend fun testSessionBuildExists(
        groupId: String,
        testSessionId: String,
        buildId: String,
    ): Boolean = transaction {
        executeQueryReturnMap(
            """
            SELECT 1
            FROM metrics.test_session_builds
            WHERE group_id = ? AND test_session_id = ? AND build_id = ?
            LIMIT 1
            """.trimIndent(),
            groupId,
            testSessionId,
            buildId
        ).isNotEmpty()
    }

    override suspend fun getTestSessionDetail(
        groupId: String,
        testSessionId: String,
        buildId: String?,
    ): Map<String, Any?>? = transaction {
        executeQueryReturnMap {
            append(
                """
            SELECT
                tsb.group_id,
                tsb.app_id,
                tsb.build_id,
                bws.build_version,
                bws.branch,
                tss.test_session_id,
                tss.test_task_id,
                tss.session_started_at,
                tss.created_by,
                tss.test_definitions,
                tss.test_launches,
                tss.result,
                tss.test_duration,
                metrics.format_duration(tss.test_duration::bigint) AS test_duration_formatted,
                tss.failed,
                tss.passed,
                tss.skipped,
                tss.smart_skipped,
                tss.success,
                tss.success_rate,
                tss.time_saved,
                metrics.format_duration_rounded(tss.time_saved::bigint) AS time_saved_formatted
            FROM metrics.test_session_builds tsb
            JOIN metrics.test_sessions_with_statistics tss
                ON tss.group_id = tsb.group_id
                AND tss.test_session_id = tsb.test_session_id
            LEFT JOIN metrics.builds_with_statistics bws
                ON bws.group_id = tsb.group_id
                AND bws.app_id = tsb.app_id
                AND bws.build_id = tsb.build_id
            WHERE tsb.group_id = ?
                AND tsb.test_session_id = ?
                """.trimIndent(), groupId, testSessionId
            )
            appendOptional(" AND tsb.build_id = ?", buildId)
            append(" ORDER BY tsb.build_id LIMIT 1 ")
        }.firstOrNull()
    }

    override suspend fun getTestSessionCoverageSummary(
        buildId: String,
        testSessionId: String,
    ): Map<String, Any?>? = transaction {
        executeQueryReturnMap(
            """
            SELECT
                total_probes,
                covered_probes,
                missed_probes,
                total_methods,
                tested_methods,
                missed_methods
            FROM metrics.get_builds_with_coverage_by_test_session(
                input_build_id => ?,
                input_test_session_id => ?
            )
            """.trimIndent(),
            buildId,
            testSessionId
        ).firstOrNull()
    }

    override suspend fun getTestDefinitionCoverageSummary(
        buildId: String,
        testSessionId: String,
        testDefinitionId: String,
    ): Map<String, Any?>? = transaction {
        executeQueryReturnMap(
            """
            SELECT
                total_probes,
                covered_probes,
                missed_probes,
                total_methods,
                tested_methods,
                missed_methods
            FROM metrics.get_builds_with_coverage_by_test_definition(
                input_build_id => ?,
                input_test_session_id => ?,
                input_test_definition_id => ?
            )
            """.trimIndent(),
            buildId,
            testSessionId,
            testDefinitionId,
        ).firstOrNull()
    }

    override suspend fun getTestSessionDefinitions(
        groupId: String,
        testSessionId: String,
        buildId: String?,
        query: String?,
        offset: Int?,
        limit: Int?,
    ): List<Map<String, Any?>> = transaction {
        executeQueryReturnMap {
            append(
                """
            SELECT
                tsd.test_definition_id,
                tsd.test_name,
                tsd.test_path,
                tsd.test_runner,
                tsd.test_result,
                tsd.test_launches
            FROM metrics.test_session_definitions tsd
            JOIN metrics.test_sessions ts
                ON ts.group_id = ?
                AND ts.test_session_id = tsd.test_session_id
            WHERE tsd.test_session_id = ?
                """.trimIndent(), groupId, testSessionId
            )
            appendTestSessionDefinitionsWhere(buildId, query)
            append(" ORDER BY tsd.test_path, tsd.test_name ")
            appendOptional(" OFFSET ?", offset)
            appendOptional(" LIMIT ?", limit)
        }
    }

    override suspend fun getTestSessionDefinitionsCount(
        groupId: String,
        testSessionId: String,
        buildId: String?,
        query: String?,
    ): Long = transaction {
        executeQueryReturnMap {
            append(
                """
            SELECT COUNT(*) AS total
            FROM metrics.test_session_definitions tsd
            JOIN metrics.test_sessions ts
                ON ts.group_id = ?
                AND ts.test_session_id = tsd.test_session_id
            WHERE tsd.test_session_id = ?
                """.trimIndent(), groupId, testSessionId
            )
            appendTestSessionDefinitionsWhere(buildId, query)
        }.firstOrNull()?.get("total")?.let {
            when (it) {
                is Long -> it
                is Number -> it.toLong()
                else -> 0L
            }
        } ?: 0L
    }

    override suspend fun getTestLaunches(
        groupId: String,
        testSessionId: String,
        buildId: String?,
        path: String?,
        testNames: List<String>,
        testResults: List<String>,
        testTags: List<String>,
        sortBy: String?,
        sortOrder: SortOrder?,
        offset: Int?,
        limit: Int?,
    ): List<Map<String, Any?>> = transaction {
        val orderBy = resolveTestLaunchesOrderBy(sortBy, sortOrder)
        executeQueryReturnMap {
            append(
                """
            SELECT
                tsd.test_definition_id,
                tsd.test_name,
                tsd.test_path,
                tsd.test_runner,
                tsd.test_tags,
                tsd.test_launches,
                tsd.test_duration_sum AS test_duration,
                tsd.test_duration_sum_formatted AS test_duration_formatted,
                tsd.test_result
            FROM metrics.test_session_definitions tsd
            JOIN metrics.test_sessions ts
                ON ts.group_id = ?
                AND ts.test_session_id = tsd.test_session_id
            WHERE tsd.test_session_id = ?
                """.trimIndent(), groupId, testSessionId
            )
            appendTestLaunchesWhere(buildId, path, testNames, testResults, testTags)
            append(" ORDER BY $orderBy ")
            appendOptional(" OFFSET ?", offset)
            appendOptional(" LIMIT ?", limit)
        }
    }

    override suspend fun getTestLaunchesCount(
        groupId: String,
        testSessionId: String,
        buildId: String?,
        path: String?,
        testNames: List<String>,
        testResults: List<String>,
        testTags: List<String>,
    ): Long = transaction {
        executeQueryReturnMap {
            append(
                """
            SELECT COUNT(*) AS total
            FROM metrics.test_session_definitions tsd
            JOIN metrics.test_sessions ts
                ON ts.group_id = ?
                AND ts.test_session_id = tsd.test_session_id
            WHERE tsd.test_session_id = ?
                """.trimIndent(), groupId, testSessionId
            )
            appendTestLaunchesWhere(buildId, path, testNames, testResults, testTags)
        }.firstOrNull()?.get("total")?.let {
            when (it) {
                is Long -> it
                is Number -> it.toLong()
                else -> 0L
            }
        } ?: 0L
    }

    override suspend fun getTestLaunchFilterOptions(
        groupId: String,
        testSessionId: String,
        buildId: String?,
        path: String?,
    ): Map<String, List<String>> = transaction {
        val names = executeQueryReturnMap {
            append(
                """
            SELECT DISTINCT tsd.test_name AS value
            FROM metrics.test_session_definitions tsd
            JOIN metrics.test_sessions ts
                ON ts.group_id = ?
                AND ts.test_session_id = tsd.test_session_id
            WHERE tsd.test_session_id = ?
                """.trimIndent(), groupId, testSessionId
            )
            appendTestLaunchesWhere(buildId, path, emptyList(), emptyList(), emptyList())
            append(" AND tsd.test_name IS NOT NULL AND tsd.test_name <> '' ORDER BY 1")
        }.mapNotNull { it["value"] as? String }

        val results = executeQueryReturnMap {
            append(
                """
            SELECT DISTINCT tsd.test_result AS value
            FROM metrics.test_session_definitions tsd
            JOIN metrics.test_sessions ts
                ON ts.group_id = ?
                AND ts.test_session_id = tsd.test_session_id
            WHERE tsd.test_session_id = ?
                """.trimIndent(), groupId, testSessionId
            )
            appendTestLaunchesWhere(buildId, path, emptyList(), emptyList(), emptyList())
            append(" AND tsd.test_result IS NOT NULL AND tsd.test_result <> '' ORDER BY 1")
        }.mapNotNull { it["value"] as? String }

        val tags = executeQueryReturnMap {
            append(
                """
            SELECT DISTINCT tag AS value
            FROM metrics.test_session_definitions tsd
            JOIN metrics.test_sessions ts
                ON ts.group_id = ?
                AND ts.test_session_id = tsd.test_session_id
            CROSS JOIN LATERAL unnest(COALESCE(tsd.test_tags, ARRAY[]::varchar[])) AS tag
            WHERE tsd.test_session_id = ?
                """.trimIndent(), groupId, testSessionId
            )
            appendTestLaunchesWhere(buildId, path, emptyList(), emptyList(), emptyList())
            append(" AND tag IS NOT NULL AND tag <> '' ORDER BY 1")
        }.mapNotNull { it["value"] as? String }

        mapOf(
            "testNames" to names,
            "testTags" to tags,
            "testResults" to results,
        )
    }

    override suspend fun getTestLaunchRowNumber(
        groupId: String,
        testSessionId: String,
        buildId: String?,
        path: String?,
        testNames: List<String>,
        testResults: List<String>,
        testTags: List<String>,
        sortBy: String?,
        sortOrder: SortOrder?,
        launchId: String,
    ): Long? = transaction {
        val orderBy = resolveTestLaunchesOrderBy(sortBy, sortOrder)
        executeQueryReturnMap {
            append(
                """
            SELECT ranked.rn
            FROM (
                SELECT
                    tsd.test_definition_id,
                    ROW_NUMBER() OVER (ORDER BY $orderBy) AS rn
                FROM metrics.test_session_definitions tsd
                JOIN metrics.test_sessions ts
                    ON ts.group_id = ?
                    AND ts.test_session_id = tsd.test_session_id
                WHERE tsd.test_session_id = ?
                """.trimIndent(), groupId, testSessionId
            )
            appendTestLaunchesWhere(buildId, path, testNames, testResults, testTags)
            append(") ranked WHERE ranked.test_definition_id = ?", launchId)
        }.firstOrNull()?.let { rowNumber(it["rn"]) }
    }

    override suspend fun getTestFileLaunches(
        groupId: String,
        testSessionId: String,
        buildId: String?,
        testPaths: List<String>,
        results: List<String>,
        sortBy: String?,
        sortOrder: SortOrder?,
        offset: Int?,
        limit: Int?,
    ): List<Map<String, Any?>> = transaction {
        val orderBy = resolveTestFileLaunchesOrderBy(sortBy, sortOrder)
        executeQueryReturnMap {
            append(
                """
            SELECT
                tfl.test_path,
                tfl.test_definitions,
                tfl.test_launches,
                tfl.result,
                tfl.failed,
                tfl.passed,
                tfl.skipped,
                tfl.smart_skipped,
                tfl.success,
                tfl.test_duration,
                metrics.format_duration(tfl.test_duration::bigint) AS test_duration_formatted,
                tfl.success_rate
            FROM metrics.test_file_launches_with_statistics tfl
            WHERE tfl.group_id = ?
                AND tfl.test_session_id = ?
                """.trimIndent(), groupId, testSessionId
            )
            appendTestFileLaunchesWhere(testPaths, results)
            append(" ORDER BY $orderBy ")
            appendOptional(" OFFSET ?", offset)
            appendOptional(" LIMIT ?", limit)
        }
    }

    override suspend fun getTestFileLaunchesCount(
        groupId: String,
        testSessionId: String,
        buildId: String?,
        testPaths: List<String>,
        results: List<String>,
    ): Long = transaction {
        if (buildId != null && !testSessionBuildExists(groupId, testSessionId, buildId)) {
            return@transaction 0L
        }
        executeQueryReturnMap {
            append(
                """
            SELECT COUNT(*) AS total
            FROM metrics.test_file_launches_with_statistics tfl
            WHERE tfl.group_id = ?
                AND tfl.test_session_id = ?
                """.trimIndent(), groupId, testSessionId
            )
            appendTestFileLaunchesWhere(testPaths, results)
        }.firstOrNull()?.get("total")?.let {
            when (it) {
                is Long -> it
                is Number -> it.toLong()
                else -> 0L
            }
        } ?: 0L
    }

    override suspend fun getTestFileLaunchFilterOptions(
        groupId: String,
        testSessionId: String,
        buildId: String?,
    ): Map<String, List<String>> = transaction {
        val testPaths = executeQueryReturnMap {
            append(
                """
            SELECT DISTINCT tfl.test_path AS value
            FROM metrics.test_file_launches_with_statistics tfl
            WHERE tfl.group_id = ?
                AND tfl.test_session_id = ?
                AND tfl.test_path IS NOT NULL AND tfl.test_path <> ''
            ORDER BY 1
                """.trimIndent(), groupId, testSessionId
            )
        }.mapNotNull { it["value"] as? String }

        val results = executeQueryReturnMap {
            append(
                """
            SELECT DISTINCT tfl.result AS value
            FROM metrics.test_file_launches_with_statistics tfl
            WHERE tfl.group_id = ?
                AND tfl.test_session_id = ?
                AND tfl.result IS NOT NULL AND tfl.result <> ''
            ORDER BY 1
                """.trimIndent(), groupId, testSessionId
            )
        }.mapNotNull { it["value"] as? String }

        mapOf(
            "testPaths" to testPaths,
            "results" to results,
        )
    }

    override suspend fun getTestFileLaunchFilterValues(
        groupId: String,
        testSessionId: String,
        query: String?,
        offset: Int?,
        limit: Int?,
    ): List<String> = transaction {
        executeQueryReturnMap {
            append(
                """
            SELECT DISTINCT tfl.test_path AS value
            FROM metrics.test_file_launches_with_statistics tfl
            WHERE tfl.group_id = ?
                AND tfl.test_session_id = ?
                AND tfl.test_path IS NOT NULL AND tfl.test_path <> ''
                """.trimIndent(), groupId, testSessionId
            )
            appendOptional(" AND tfl.test_path ILIKE ?", query, transform = { "%$it%" })
            append(" ORDER BY 1 ")
            appendOptional(" OFFSET ?", offset)
            appendOptional(" LIMIT ?", limit)
        }.mapNotNull { it["value"] as? String }
    }

    override suspend fun getTestFileLaunchFilterValuesCount(
        groupId: String,
        testSessionId: String,
        query: String?,
    ): Long = transaction {
        executeQueryReturnMap {
            append(
                """
            SELECT COUNT(DISTINCT tfl.test_path) AS total
            FROM metrics.test_file_launches_with_statistics tfl
            WHERE tfl.group_id = ?
                AND tfl.test_session_id = ?
                AND tfl.test_path IS NOT NULL AND tfl.test_path <> ''
                """.trimIndent(), groupId, testSessionId
            )
            appendOptional(" AND tfl.test_path ILIKE ?", query, transform = { "%$it%" })
        }.firstOrNull()?.get("total").let(::totalAsLong)
    }

    override suspend fun getTestFileLaunchRowNumber(
        groupId: String,
        testSessionId: String,
        buildId: String?,
        testPaths: List<String>,
        results: List<String>,
        sortBy: String?,
        sortOrder: SortOrder?,
        path: String,
    ): Long? = transaction {
        if (buildId != null && !testSessionBuildExists(groupId, testSessionId, buildId)) {
            return@transaction null
        }
        val orderBy = resolveTestFileLaunchesOrderBy(sortBy, sortOrder)
        executeQueryReturnMap {
            append(
                """
            SELECT ranked.rn
            FROM (
                SELECT
                    tfl.test_path,
                    ROW_NUMBER() OVER (ORDER BY $orderBy) AS rn
                FROM metrics.test_file_launches_with_statistics tfl
                WHERE tfl.group_id = ?
                    AND tfl.test_session_id = ?
                """.trimIndent(), groupId, testSessionId
            )
            appendTestFileLaunchesWhere(testPaths, results)
            append(") ranked WHERE ranked.test_path = ?", path)
        }.firstOrNull()?.let { rowNumber(it["rn"]) }
    }

    private fun rowNumber(value: Any?): Long? = when (value) {
        is Long -> value
        is Number -> value.toLong()
        else -> null
    }

    private fun SqlBuilder.appendTestSessionDefinitionsWhere(
        buildId: String?,
        query: String?,
    ) {
        val pattern = query?.takeIf { it.isNotBlank() }?.let { "%$it%" }
        appendOptional(
            " AND (tsd.test_definition_id ILIKE ? OR tsd.test_name ILIKE ? OR COALESCE(tsd.test_path, '') ILIKE ?)",
            pattern,
            pattern,
            pattern,
        )
        if (buildId != null) {
            append(
                """
                 AND EXISTS (
                    SELECT 1
                    FROM metrics.test_session_builds tsb
                    WHERE tsb.group_id = ts.group_id
                        AND tsb.test_session_id = ts.test_session_id
                        AND tsb.build_id = ?
                )
                """.trimIndent(), buildId
            )
        }
    }

    private fun SqlBuilder.appendTestLaunchesWhere(
        buildId: String?,
        path: String?,
        testNames: List<String>,
        testResults: List<String>,
        testTags: List<String>,
    ) {
        appendOptional(" AND tsd.test_path = ?", path)
        appendOptional(" AND tsd.test_name = ANY(?)", testNames)
        appendOptional(" AND tsd.test_result = ANY(?)", testResults)
        appendOptional(" AND tsd.test_tags && ?::varchar[]", testTags)
        if (buildId != null) {
            append(
                """
                 AND EXISTS (
                    SELECT 1
                    FROM metrics.test_session_builds tsb
                    WHERE tsb.group_id = ts.group_id
                        AND tsb.test_session_id = ts.test_session_id
                        AND tsb.build_id = ?
                )
                """.trimIndent(), buildId
            )
        }
    }

    private fun SqlBuilder.appendTestFileLaunchesWhere(
        testPaths: List<String>,
        results: List<String>,
    ) {
        appendOptional(" AND tfl.test_path = ANY(?)", testPaths)
        appendOptional(" AND tfl.result = ANY(?)", results)
    }

    private fun resolveTestFileLaunchesOrderBy(sortBy: String?, sortOrder: SortOrder?): String {
        val direction = sqlSortDirection(sortOrder, default = SortOrder.ASC)
        return when (sortBy) {
            "testDefinitions" -> "tfl.test_definitions $direction NULLS LAST, tfl.test_path ASC"
            "testLaunches" -> "tfl.test_launches $direction NULLS LAST, tfl.test_path ASC"
            "passed" -> "tfl.passed $direction NULLS LAST, tfl.test_path ASC"
            "failed" -> "tfl.failed $direction NULLS LAST, tfl.test_path ASC"
            "skipped" -> "tfl.skipped $direction NULLS LAST, tfl.test_path ASC"
            "smartSkipped" -> "tfl.smart_skipped $direction NULLS LAST, tfl.test_path ASC"
            "testDuration" -> "tfl.test_duration $direction NULLS LAST, tfl.test_path ASC"
            "successRate" -> "tfl.success_rate $direction NULLS LAST, tfl.test_path ASC"
            else -> "tfl.test_path ASC"
        }
    }

    private fun resolveTestLaunchesOrderBy(sortBy: String?, sortOrder: SortOrder?): String {
        val direction = sqlSortDirection(sortOrder, default = SortOrder.ASC)
        return when (sortBy) {
            "testLaunches" -> "tsd.test_launches $direction NULLS LAST, tsd.test_path ASC, tsd.test_name ASC"
            "testDuration" -> "tsd.test_duration_sum $direction NULLS LAST, tsd.test_path ASC, tsd.test_name ASC"
            else -> "tsd.test_path ASC, tsd.test_name ASC"
        }
    }

    private enum class TestSessionDistinctColumn {
        TEST_TASK_ID,
        CREATED_BY,
        RESULT,
    }

    private fun testSessionFilterField(field: String): TestSessionDistinctColumn = when (field) {
        "testTaskIds" -> TestSessionDistinctColumn.TEST_TASK_ID
        "createdBys" -> TestSessionDistinctColumn.CREATED_BY
        "results" -> TestSessionDistinctColumn.RESULT
        else -> throw IllegalArgumentException(
            "Invalid field '$field'. Allowed values: testTaskIds, createdBys, results"
        )
    }

    private fun testSessionDistinctColumnSql(column: TestSessionDistinctColumn): String = when (column) {
        TestSessionDistinctColumn.TEST_TASK_ID -> "tss.test_task_id"
        TestSessionDistinctColumn.CREATED_BY -> "tss.created_by"
        TestSessionDistinctColumn.RESULT -> "tss.result"
    }

    private suspend fun getTestSessionDistinctValues(
        groupId: String,
        buildId: String?,
        column: TestSessionDistinctColumn,
        query: String? = null,
        offset: Int? = null,
        limit: Int? = null,
    ): List<String> = transaction {
        val sqlColumn = testSessionDistinctColumnSql(column)
        executeQueryReturnMap {
            append(
                """
            SELECT DISTINCT $sqlColumn AS value
            FROM metrics.test_session_builds tsb
            JOIN metrics.test_sessions_with_statistics tss
                ON tss.group_id = tsb.group_id
                AND tss.test_session_id = tsb.test_session_id
            WHERE tsb.group_id = ?
                """.trimIndent(),
                groupId,
            )
            appendOptional(" AND tsb.build_id = ?", buildId)
            append(" AND $sqlColumn IS NOT NULL AND $sqlColumn::text <> '' ")
            appendOptional(" AND $sqlColumn::text ILIKE ?", query, transform = { "%$it%" })
            append(" ORDER BY value ")
            appendOptional(" OFFSET ?", offset)
            appendOptional(" LIMIT ?", limit)
        }.mapNotNull { it["value"] as? String }
    }

    private suspend fun getTestSessionDistinctValuesCount(
        groupId: String,
        buildId: String?,
        column: TestSessionDistinctColumn,
        query: String? = null,
    ): Long = transaction {
        val sqlColumn = testSessionDistinctColumnSql(column)
        executeQueryReturnMap {
            append(
                """
            SELECT COUNT(DISTINCT $sqlColumn) AS total
            FROM metrics.test_session_builds tsb
            JOIN metrics.test_sessions_with_statistics tss
                ON tss.group_id = tsb.group_id
                AND tss.test_session_id = tsb.test_session_id
            WHERE tsb.group_id = ?
                """.trimIndent(),
                groupId,
            )
            appendOptional(" AND tsb.build_id = ?", buildId)
            append(" AND $sqlColumn IS NOT NULL AND $sqlColumn::text <> '' ")
            appendOptional(" AND $sqlColumn::text ILIKE ?", query, transform = { "%$it%" })
        }.firstOrNull()?.get("total").let(::totalAsLong)
    }

    private fun SqlBuilder.appendTestSessionFilters(
        testTaskIds: List<String>,
        createdBys: List<String>,
        results: List<String>,
    ) {
        appendOptional(" AND tss.test_task_id = ANY(?)", testTaskIds)
        appendOptional(" AND tss.created_by = ANY(?)", createdBys)
        appendOptional(" AND tss.result = ANY(?)", results)
    }

    private fun resolveTestSessionOrderBy(sortBy: String?, sortOrder: SortOrder?): String {
        val column = when (sortBy) {
            "sessionStartedAt" -> "tss.session_started_at"
            "successRate" -> "tss.success_rate"
            else -> "tss.session_started_at"
        }
        val direction = if (sortBy == null) {
            "DESC"
        } else {
            sqlSortDirection(sortOrder, default = SortOrder.DESC)
        }
        return "$column $direction NULLS LAST, tss.test_session_id ASC"
    }

    override suspend fun getBuildsCount(
        groupId: String, appId: String,
        branches: List<String>, envIds: List<String>,
        commitSha: String?,
        buildVersion: String?,
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
            appendOptional(" AND b.commit_sha = ?", commitSha)
            appendOptional(" AND b.build_version = ?", buildVersion)
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
        sortBy: String?,
        sortOrder: SortOrder?,
        offset: Int?, limit: Int?
    ): List<Map<String, Any?>> = transaction {
        val sortDirection = sqlSortDirection(sortOrder)
        val orderBy = when (sortBy) {
            "isolated_probes_coverage_ratio" ->
                "isolated_probes_coverage_ratio $sortDirection, method_id ASC"
            "probes_count" -> "probes_count $sortDirection, method_id ASC"
            "isolated_covered_probes" -> "isolated_covered_probes $sortDirection, method_id ASC"
            else -> "method_id ASC"
        }

        executeQueryReturnMap {
            append(
                """
                SELECT 
                    method_id,
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
                ORDER BY $orderBy    
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
        sortBy: String?,
        sortOrder: SortOrder?,
        offset: Int?,
        limit: Int?,
    ): List<Map<String, Any?>> = transaction {
        val sortDirection = sqlSortDirection(sortOrder)
        val orderBy = when (sortBy) {
            "probes_coverage_ratio" -> "probes_coverage_ratio $sortDirection, method_id ASC"
            "probes_count" -> "probes_count $sortDirection, method_id ASC"
            "covered_probes" -> "covered_probes $sortDirection, method_id ASC"
            else -> "signature ASC"
        }

        executeQueryReturnMap {
            append(
                """
                SELECT 
                    method_id,
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
                ORDER BY $orderBy
                """.trimIndent()
            )
            appendOptional(" OFFSET ?", offset)
            appendOptional(" LIMIT ?", limit)
        }
    }

    override suspend fun getMethodsWithCoverageByTestSessionCount(
        buildId: String,
        testSessionId: String,
        testTags: List<String>,
        packageNamePattern: String?,
        methodSignaturePattern: String?,
        coverageAppEnvIds: List<String>,
    ): Long = transaction {
        executeQueryReturnMap {
            append(
                """
            SELECT COUNT(*) AS cnt
            FROM metrics.get_methods_with_coverage_by_test_session(
                input_build_id => ?,
                input_test_session_id => ?
                """.trimIndent(), buildId, testSessionId
            )
            appendOptional(", input_coverage_test_tags => ?", testTags)
            appendOptional(", input_package_name_pattern => ?", packageNamePattern) { "$it%" }
            appendOptional(", input_signature_pattern => ?", methodSignaturePattern)
            appendOptional(", input_coverage_app_env_ids => ?", coverageAppEnvIds)
            append(" ) ")
        }.firstOrNull()?.get("cnt")?.let {
            when (it) {
                is Long -> it
                is Number -> it.toLong()
                else -> 0L
            }
        } ?: 0L
    }

    override suspend fun getMethodsWithCoverageByTestDefinition(
        buildId: String,
        testSessionId: String,
        testDefinitionId: String,
        packageNamePattern: String?,
        methodSignaturePattern: String?,
        coverageAppEnvIds: List<String>,
        sortBy: String?,
        sortOrder: SortOrder?,
        offset: Int?,
        limit: Int?,
    ): List<Map<String, Any?>> = transaction {
        val sortDirection = sqlSortDirection(sortOrder)
        val orderBy = when (sortBy) {
            "probes_coverage_ratio" -> "probes_coverage_ratio $sortDirection, method_id ASC"
            "probes_count" -> "probes_count $sortDirection, method_id ASC"
            "covered_probes" -> "covered_probes $sortDirection, method_id ASC"
            else -> "signature ASC"
        }

        executeQueryReturnMap {
            append(
                """
                SELECT 
                    method_id,
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
                ORDER BY $orderBy
                """.trimIndent()
            )
            appendOptional(" OFFSET ?", offset)
            appendOptional(" LIMIT ?", limit)
        }
    }

    override suspend fun getMethodsWithCoverageByTestDefinitionCount(
        buildId: String,
        testSessionId: String,
        testDefinitionId: String,
        packageNamePattern: String?,
        methodSignaturePattern: String?,
        coverageAppEnvIds: List<String>,
    ): Long = transaction {
        executeQueryReturnMap {
            append(
                """
            SELECT COUNT(*) AS cnt
            FROM metrics.get_methods_with_coverage_by_test_definition(
                input_build_id => ?,
                input_test_session_id => ?,
                input_test_definition_id => ?
                """.trimIndent(), buildId, testSessionId, testDefinitionId
            )
            appendOptional(", input_package_name_pattern => ?", packageNamePattern) { "$it%" }
            appendOptional(", input_signature_pattern => ?", methodSignaturePattern)
            appendOptional(", input_coverage_app_env_ids => ?", coverageAppEnvIds)
            append(" ) ")
        }.firstOrNull()?.get("cnt")?.let {
            when (it) {
                is Long -> it
                is Number -> it.toLong()
                else -> 0L
            }
        } ?: 0L
    }

    override suspend fun getClassCoverageByTestSession(
        buildId: String,
        testSessionId: String,
        packageName: String?,
        testTags: List<String>,
        coverageAppEnvIds: List<String>,
        sortBy: String?,
        sortOrder: SortOrder?,
        offset: Int?,
        limit: Int?,
    ): List<Map<String, Any?>> = transaction {
        val sortDirection = sqlSortDirection(sortOrder)
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
                        COUNT(*) FILTER (WHERE covered_probes > 0)::INT AS covered_methods,
                        COUNT(*) FILTER (WHERE covered_probes > 0)::INT AS aggregated_covered_methods,
                        (COUNT(*) - COUNT(*) FILTER (WHERE covered_probes > 0))::INT AS missed_methods,
                        COALESCE(SUM(probes_count), 0)::INT AS probes_count,
                        COALESCE(SUM(covered_probes), 0)::INT AS covered_probes,
                        COALESCE(SUM(covered_probes), 0)::INT AS aggregated_covered_probes,
                        (COALESCE(SUM(probes_count), 0) - COALESCE(SUM(covered_probes), 0))::INT AS missed_probes
                    FROM metrics.get_methods_with_coverage_by_test_session(
                        input_build_id => ?,
                        input_test_session_id => ?
                """.trimIndent(), buildId, testSessionId
            )
            appendOptional(", input_package_name_pattern => ?", packageName) { "$it%" }
            appendOptional(", input_coverage_test_tags => ?", testTags)
            appendOptional(", input_coverage_app_env_ids => ?", coverageAppEnvIds)
            append(
                """
                    )
                    GROUP BY class_name
                ) AS class_coverage
                """.trimIndent()
            )
            appendOptional(" WHERE substring(class_name from '^(.*)/') = ?", packageName)
            append(" ORDER BY $orderBy")
            appendOptional(" OFFSET ?", offset)
            appendOptional(" LIMIT ?", limit)
        }
    }

    override suspend fun getClassCoverageByTestSessionCount(
        buildId: String,
        testSessionId: String,
        packageName: String?,
        testTags: List<String>,
        coverageAppEnvIds: List<String>,
    ): Long = transaction {
        executeQueryReturnMap {
            append(
                """
                SELECT COUNT(*) AS cnt
                FROM (
                    SELECT class_name
                    FROM metrics.get_methods_with_coverage_by_test_session(
                        input_build_id => ?,
                        input_test_session_id => ?
                """.trimIndent(), buildId, testSessionId
            )
            appendOptional(", input_package_name_pattern => ?", packageName) { "$it%" }
            appendOptional(", input_coverage_test_tags => ?", testTags)
            appendOptional(", input_coverage_app_env_ids => ?", coverageAppEnvIds)
            append(
                """
                    )
                    GROUP BY class_name
                ) AS class_coverage
                """.trimIndent()
            )
            appendOptional(" WHERE substring(class_name from '^(.*)/') = ?", packageName)
        }.firstOrNull()?.get("cnt")?.let {
            when (it) {
                is Long -> it
                is Number -> it.toLong()
                else -> 0L
            }
        } ?: 0L
    }

    override suspend fun getClassCoverageByTestDefinition(
        buildId: String,
        testSessionId: String,
        testDefinitionId: String,
        packageName: String?,
        coverageAppEnvIds: List<String>,
        sortBy: String?,
        sortOrder: SortOrder?,
        offset: Int?,
        limit: Int?,
    ): List<Map<String, Any?>> = transaction {
        val sortDirection = sqlSortDirection(sortOrder)
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
                        COUNT(*) FILTER (WHERE covered_probes > 0)::INT AS covered_methods,
                        COUNT(*) FILTER (WHERE covered_probes > 0)::INT AS aggregated_covered_methods,
                        (COUNT(*) - COUNT(*) FILTER (WHERE covered_probes > 0))::INT AS missed_methods,
                        COALESCE(SUM(probes_count), 0)::INT AS probes_count,
                        COALESCE(SUM(covered_probes), 0)::INT AS covered_probes,
                        COALESCE(SUM(covered_probes), 0)::INT AS aggregated_covered_probes,
                        (COALESCE(SUM(probes_count), 0) - COALESCE(SUM(covered_probes), 0))::INT AS missed_probes
                    FROM metrics.get_methods_with_coverage_by_test_definition(
                        input_build_id => ?,
                        input_test_session_id => ?,
                        input_test_definition_id => ?
                """.trimIndent(), buildId, testSessionId, testDefinitionId
            )
            appendOptional(", input_package_name_pattern => ?", packageName) { "$it%" }
            appendOptional(", input_coverage_app_env_ids => ?", coverageAppEnvIds)
            append(
                """
                    )
                    GROUP BY class_name
                ) AS class_coverage
                """.trimIndent()
            )
            appendOptional(" WHERE substring(class_name from '^(.*)/') = ?", packageName)
            append(" ORDER BY $orderBy")
            appendOptional(" OFFSET ?", offset)
            appendOptional(" LIMIT ?", limit)
        }
    }

    override suspend fun getClassCoverageByTestDefinitionCount(
        buildId: String,
        testSessionId: String,
        testDefinitionId: String,
        packageName: String?,
        coverageAppEnvIds: List<String>,
    ): Long = transaction {
        executeQueryReturnMap {
            append(
                """
                SELECT COUNT(*) AS cnt
                FROM (
                    SELECT class_name
                    FROM metrics.get_methods_with_coverage_by_test_definition(
                        input_build_id => ?,
                        input_test_session_id => ?,
                        input_test_definition_id => ?
                """.trimIndent(), buildId, testSessionId, testDefinitionId
            )
            appendOptional(", input_package_name_pattern => ?", packageName) { "$it%" }
            appendOptional(", input_coverage_app_env_ids => ?", coverageAppEnvIds)
            append(
                """
                    )
                    GROUP BY class_name
                ) AS class_coverage
                """.trimIndent()
            )
            appendOptional(" WHERE substring(class_name from '^(.*)/') = ?", packageName)
        }.firstOrNull()?.get("cnt")?.let {
            when (it) {
                is Long -> it
                is Number -> it.toLong()
                else -> 0L
            }
        } ?: 0L
    }

    override suspend fun getBuildChanges(
        buildId: String,
        baselineBuildId: String,
        groupId: String,
        appId: String,
        coverageTestTags: List<String>,
        coverageAppEnvIds: List<String>,
        coverageBranches: List<String>,
        changeTypes: List<String>,
        hasImpactedTests: Boolean?,
        methodSignature: String?,
        testDefinitionId: String?,
        sortBy: String?,
        sortOrder: SortOrder?,
        offset: Int?,
        limit: Int?,
    ): List<Map<String, Any?>> = transaction {
        val orderBy = resolveBuildChangeOrderBy(sortBy, sortOrder)
        executeQueryReturnMap {
            append(
                """
                SELECT
                    c.change_type,
                    c.class_name,
                    c.method_name,
                    c.method_params,
                    c.return_type,
                    c.probes_count,
                    c.isolated_covered_probes,
                    c.isolated_missed_probes,
                    c.isolated_probes_coverage_ratio,
                    c.aggregated_covered_probes,
                    c.aggregated_missed_probes,
                    c.aggregated_probes_coverage_ratio,
                    c.signature,
                    COALESCE(i.impacted_tests, 0) AS impacted_tests
                """.trimIndent()
            )
            appendBuildChangesFromClause(
                buildId = buildId,
                baselineBuildId = baselineBuildId,
                groupId = groupId,
                appId = appId,
                coverageTestTags = coverageTestTags,
                coverageAppEnvIds = coverageAppEnvIds,
                coverageBranches = coverageBranches,
                changeTypes = changeTypes,
                hasImpactedTests = hasImpactedTests,
                methodSignature = methodSignature,
                testDefinitionId = testDefinitionId,
            )
            append(" ORDER BY $orderBy")
            appendOptional(" OFFSET ?", offset)
            appendOptional(" LIMIT ?", limit)
        }
    }

    override suspend fun getBuildChangesCount(
        buildId: String,
        baselineBuildId: String,
        groupId: String,
        appId: String,
        coverageTestTags: List<String>,
        coverageAppEnvIds: List<String>,
        coverageBranches: List<String>,
        changeTypes: List<String>,
        hasImpactedTests: Boolean?,
        methodSignature: String?,
        testDefinitionId: String?,
    ): Long = transaction {
        val result = executeQueryReturnMap {
            append(" SELECT COUNT(*) AS cnt ")
            appendBuildChangesFromClause(
                buildId = buildId,
                baselineBuildId = baselineBuildId,
                groupId = groupId,
                appId = appId,
                coverageTestTags = coverageTestTags,
                coverageAppEnvIds = coverageAppEnvIds,
                coverageBranches = coverageBranches,
                changeTypes = changeTypes,
                hasImpactedTests = hasImpactedTests,
                methodSignature = methodSignature,
                testDefinitionId = testDefinitionId,
            )
        }
        (result.firstOrNull()?.get("cnt") as? Number)?.toLong() ?: 0
    }

    private fun SqlBuilder.appendBuildChangesFromClause(
        buildId: String,
        baselineBuildId: String,
        groupId: String,
        appId: String,
        coverageTestTags: List<String>,
        coverageAppEnvIds: List<String>,
        coverageBranches: List<String>,
        changeTypes: List<String>,
        hasImpactedTests: Boolean?,
        methodSignature: String?,
        testDefinitionId: String?,
    ) {
        append("\n")
        append(
            """
                FROM metrics.get_changes_with_coverage(
                    input_build_id => ?,
                    input_baseline_build_id => ?
            """.trimIndent(), buildId, baselineBuildId
        )
        appendCoverageFilterParams(coverageTestTags, coverageAppEnvIds, coverageBranches)
        val includeEqual = changeTypes.any { it.equals("equal", ignoreCase = true) }
        appendOptional(", include_deleted => ?", true) { it }
        appendOptional(", include_equal => ?", includeEqual) { it }
        append(
            """
                ) c
                LEFT JOIN metrics.get_impacted_methods_v2(
                    input_build_id => ?,
                    input_baseline_build_id => ?
            """.trimIndent(), buildId, baselineBuildId
        )
        appendOptional(", input_test_tags => ?", coverageTestTags)
        append(
            """
                ) i ON c.signature = i.signature
                WHERE 1 = 1
            """.trimIndent()
        )
        appendOptional(" AND c.change_type = ANY(?)", changeTypes)
        if (hasImpactedTests == true) {
            append(" AND COALESCE(i.impacted_tests, 0) > 0")
        }
        appendOptional(" AND c.signature = ?", methodSignature)
        if (testDefinitionId != null) {
            append(
                """
                 AND EXISTS (
                    SELECT 1
                    FROM metrics.test_to_code_mapping tc
                    WHERE tc.group_id = ?
                        AND tc.app_id = ?
                        AND tc.signature = c.signature
                        AND tc.test_definition_id = ?
                )
                """.trimIndent(), groupId, appId, testDefinitionId
            )
        }
    }

    private fun resolveBuildChangeOrderBy(sortBy: String?, sortOrder: SortOrder?): String {
        val direction = sqlSortDirection(
            sortOrder,
            default = if (sortBy == null) SortOrder.DESC else SortOrder.ASC
        )
        val column = when (sortBy) {
            "changeType" -> "c.change_type"
            "coverageRatioInOtherBuilds" -> "c.aggregated_probes_coverage_ratio"
            "impactedTests" -> "impacted_tests"
            "signature" -> "c.signature"
            null, "aggregatedMissedProbes" -> "c.aggregated_missed_probes"
            else -> "c.aggregated_missed_probes"
        }
        return "$column $direction NULLS LAST, c.signature ASC"
    }

    private fun resolveImpactedTestsOrderBy(sortBy: String?, sortOrder: SortOrder?): String? {
        val column = when (sortBy) {
            "test_path" -> "test_path"
            "test_name" -> "test_name"
            "test_runner" -> "test_runner"
            "impacted_methods" -> "impacted_methods"
            else -> return null
        }
        return "$column ${sqlSortDirection(sortOrder)}"
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
                    COUNT(*) FILTER (WHERE aggregated_covered_probes > 0)::INT AS aggregated_covered_methods,
                    (COUNT(*) - COUNT(*) FILTER (WHERE isolated_covered_probes > 0))::INT AS missed_methods,
                    COALESCE(SUM(probes_count), 0)::INT AS probes_count,
                    COALESCE(SUM(isolated_covered_probes), 0)::INT AS covered_probes,
                    COALESCE(SUM(aggregated_covered_probes), 0)::INT AS aggregated_covered_probes,
                    (COALESCE(SUM(probes_count), 0) - COALESCE(SUM(isolated_covered_probes), 0))::INT AS missed_probes
                FROM (
                    SELECT
                        $PACKAGE_NAME_SQL AS package_name,
                        probes_count,
                        isolated_covered_probes,
                        aggregated_covered_probes
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
        val sortDirection = sqlSortDirection(sortOrder)
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
                        COUNT(*) FILTER (WHERE aggregated_covered_probes > 0)::INT AS aggregated_covered_methods,
                        (COUNT(*) - COUNT(*) FILTER (WHERE isolated_covered_probes > 0))::INT AS missed_methods,
                        COALESCE(SUM(probes_count), 0)::INT AS probes_count,
                        COALESCE(SUM(isolated_covered_probes), 0)::INT AS covered_probes,
                        COALESCE(SUM(aggregated_covered_probes), 0)::INT AS aggregated_covered_probes,
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
                """.trimIndent()
            )
            appendOptional(" WHERE substring(class_name from '^(.*)/') = ?", packageName)
            append(" ORDER BY $orderBy")
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
            // Match the direct-package restriction applied in getClassCoverage so the
            // total count stays consistent with the returned rows.
            appendOptional(" WHERE substring(class_name from '^(.*)/') = ?", packageName)
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
                        FROM metrics.get_impacted_tests_v3(
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

    override suspend fun getImpactedTests(
        targetBuildId: String,
        baselineBuildId: String,
        testTaskId: String?,
        testTags: List<String>,
        testPathPattern: String?,
        testNamePattern: String?,
        testRunner: String?,
        packageNamePattern: String?,
        methodSignaturePattern: String?,
        excludeMethodSignatures: List<String>,
        coverageBranches: List<String>,
        coverageAppEnvIds: List<String>,
        testDefinitionId: String?,
        impactStatuses: List<TestImpactStatus>,
        sortBy: String?,
        sortOrder: SortOrder?,
        offset: Int?,
        limit: Int?
    ): List<Map<String, Any?>> = transaction {
        val orderBy = resolveImpactedTestsOrderBy(sortBy, sortOrder)

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
                    impact_status,
                    impacted_methods                 
                FROM metrics.get_impacted_tests_v3(
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

            appendOptional(", input_impact_statuses => ?", impactStatuses.map { it.name })

            append(
                """
                )
            """.trimIndent()
            )
            appendImpactedTestsResultFilters(testDefinitionId, testRunner)

            if (orderBy != null) {
                append(" ORDER BY $orderBy")
            }

            appendOptional(" OFFSET ?", offset)
            appendOptional(" LIMIT ?", limit)
        }
    }

    override suspend fun getImpactedTestsCount(
        targetBuildId: String,
        baselineBuildId: String,

        testTaskId: String?,
        testTags: List<String>,
        testPathPattern: String?,
        testNamePattern: String?,
        testRunner: String?,

        packageNamePattern: String?,
        methodSignaturePattern: String?,
        excludeMethodSignatures: List<String>,

        coverageBranches: List<String>,
        coverageAppEnvIds: List<String>,

        testDefinitionId: String?,
        impactStatuses: List<TestImpactStatus>,
    ): Long = transaction {
        val result = executeQueryReturnMap {
            append(
                """
                SELECT COUNT(*) AS cnt
                FROM metrics.get_impacted_tests_v3(
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

            appendOptional(", input_impact_statuses => ?", impactStatuses.map { it.name })

            append(
                """
                )
            """.trimIndent()
            )
            appendImpactedTestsResultFilters(testDefinitionId, testRunner)
        }
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
        val sortDirection = sqlSortDirection(sortOrder, default = SortOrder.ASC)

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

    override suspend fun getImpactedTestsFilterOptions(
        targetBuildId: String,
        baselineBuildId: String,
        packageNamePattern: String?,
        methodSignaturePattern: String?,
        excludeMethodSignatures: List<String>,
        coverageBranches: List<String>,
        coverageAppEnvIds: List<String>,
    ): Map<String, List<String>> = transaction {
        fun SqlBuilder.appendImpactedTestsFilterParams() {
            appendOptional(", input_package_name_pattern => ?", packageNamePattern) { "$it%" }
            appendOptional(", input_method_signature_pattern => ?", methodSignaturePattern)
            appendOptional(", input_exclude_method_signatures => ?", excludeMethodSignatures)
            appendOptional(", input_coverage_branches => ?", coverageBranches)
            appendOptional(", input_coverage_app_env_ids => ?", coverageAppEnvIds)
        }

        val paths = executeQueryReturnMap {
            append(
                """
                SELECT DISTINCT test_path AS value
                FROM metrics.get_impacted_tests_v2(
                    input_build_id => ?,
                    input_baseline_build_id => ?
                """.trimIndent(), targetBuildId, baselineBuildId
            )
            appendImpactedTestsFilterParams()
            append(
                """
                )
                WHERE test_path IS NOT NULL AND test_path <> ''
                ORDER BY 1
                """.trimIndent()
            )
        }.mapNotNull { it["value"] as? String }

        val names = executeQueryReturnMap {
            append(
                """
                SELECT DISTINCT test_name AS value
                FROM metrics.get_impacted_tests_v2(
                    input_build_id => ?,
                    input_baseline_build_id => ?
                """.trimIndent(), targetBuildId, baselineBuildId
            )
            appendImpactedTestsFilterParams()
            append(
                """
                )
                WHERE test_name IS NOT NULL AND test_name <> ''
                ORDER BY 1
                """.trimIndent()
            )
        }.mapNotNull { it["value"] as? String }

        val runners = executeQueryReturnMap {
            append(
                """
                SELECT DISTINCT test_runner AS value
                FROM metrics.get_impacted_tests_v2(
                    input_build_id => ?,
                    input_baseline_build_id => ?
                """.trimIndent(), targetBuildId, baselineBuildId
            )
            appendImpactedTestsFilterParams()
            append(
                """
                )
                WHERE test_runner IS NOT NULL AND test_runner <> ''
                ORDER BY 1
                """.trimIndent()
            )
        }.mapNotNull { it["value"] as? String }

        val tags = executeQueryReturnMap {
            append(
                """
                SELECT DISTINCT tag AS value
                FROM metrics.get_impacted_tests_v2(
                    input_build_id => ?,
                    input_baseline_build_id => ?
                """.trimIndent(), targetBuildId, baselineBuildId
            )
            appendImpactedTestsFilterParams()
            append(
                """
                ) t
                CROSS JOIN LATERAL unnest(COALESCE(t.test_tags, ARRAY[]::varchar[])) AS tag
                WHERE tag IS NOT NULL AND tag <> ''
                ORDER BY 1
                """.trimIndent()
            )
        }.mapNotNull { it["value"] as? String }

        mapOf(
            "testPaths" to paths,
            "testNames" to names,
            "testRunners" to runners,
            "testTags" to tags,
        )
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

private fun totalAsLong(value: Any?): Long =
    when (value) {
        is Long -> value
        is Number -> value.toLong()
        else -> 0L
    }

private fun SqlBuilder.appendImpactedTestsResultFilters(
    testDefinitionId: String?,
    testRunner: String?,
) {
    val filters = buildList {
        if (!testDefinitionId.isNullOrBlank()) {
            add("test_definition_id = ?" to testDefinitionId)
        }
        if (!testRunner.isNullOrBlank()) {
            add("test_runner = ?" to testRunner)
        }
    }
    if (filters.isEmpty()) {
        return
    }
    append(" WHERE ")
    filters.forEachIndexed { index, (condition, value) ->
        if (index > 0) {
            append(" AND ")
        }
        append(condition, value)
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
