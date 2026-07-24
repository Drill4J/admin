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
package com.epam.drill.admin.writer.rawdata.repository.impl

import com.epam.drill.admin.common.config.executeQueryReturnMap
import com.epam.drill.admin.writer.rawdata.repository.RawMethodBrowseRepository
import com.epam.drill.admin.writer.rawdata.views.RawMethodLeaf
import com.epam.drill.admin.writer.rawdata.views.RawMethodPageView
import com.epam.drill.admin.writer.rawdata.views.RawMethodView
import org.jetbrains.exposed.sql.transactions.TransactionManager

class RawMethodBrowseRepositoryImpl : RawMethodBrowseRepository {
    override fun getMethodLeaves(
        groupId: String,
        appId: String,
        buildId: String,
    ): List<RawMethodLeaf> {
        return query(
            """
            $CANDIDATE_METHODS_CTE
            SELECT
                method_id,
                method_name,
                class_name,
                COALESCE(substring(class_name from '^(.*)/'), '') AS package_name,
                signature,
                method_params,
                return_type,
                probes_count,
                ignored
            FROM candidate_methods
            ORDER BY class_name, probe_start_pos ASC, method_name, method_id
            """.trimIndent(),
            groupId, appId, buildId,
        ).map { row ->
            RawMethodLeaf(
                methodId = row.string("method_id"),
                methodName = row.string("method_name"),
                className = row.string("class_name"),
                packageName = row.string("package_name"),
                signature = row.string("signature"),
                methodParams = row.string("method_params"),
                returnType = row.string("return_type"),
                probesCount = row.int("probes_count"),
                ignored = row["ignored"] as Boolean,
            )
        }
    }

    override fun getMethods(
        groupId: String,
        appId: String,
        buildId: String,
        className: String,
        page: Int,
        pageSize: Int,
    ): RawMethodPageView {
        val offset = (page - 1) * pageSize
        val rows = query(
            """
            $CANDIDATE_METHODS_CTE
            SELECT
                method_id,
                method_name,
                class_name,
                signature,
                method_params,
                return_type,
                probes_count,
                ignored,
                matching_rule_ids,
                COUNT(*) OVER() AS total
            FROM candidate_methods
            WHERE class_name = ?
            ORDER BY probe_start_pos ASC, method_name, method_id
            LIMIT ? OFFSET ?
            """.trimIndent(),
            groupId, appId, buildId, className, pageSize, offset,
        )
        return RawMethodPageView(
            data = rows.map { row ->
                RawMethodView(
                    methodId = row.string("method_id"),
                    methodName = row.string("method_name"),
                    className = row.string("class_name"),
                    signature = row.string("signature"),
                    methodParams = row.string("method_params"),
                    returnType = row.string("return_type"),
                    probesCount = row.int("probes_count"),
                    ignored = row["ignored"] as Boolean,
                    matchingRuleIds = row.numberList("matching_rule_ids"),
                )
            },
            page = page,
            pageSize = pageSize,
            total = (rows.firstOrNull()?.get("total") as? Number)?.toLong() ?: 0,
        )
    }

    private fun query(
        sql: String,
        vararg params: Any?,
    ): List<Map<String, Any?>> {
        return TransactionManager.current().executeQueryReturnMap(sql, *params)
    }

    private fun Map<String, Any?>.string(name: String): String = this[name] as? String ?: ""
    private fun Map<String, Any?>.int(name: String): Int = (this[name] as Number).toInt()
    private fun Map<String, Any?>.numberList(name: String): List<Int> =
        (this[name] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()

    private companion object {
        private const val SAVED_RULE_MATCH = """
            (r.classname_pattern IS NULL OR m.class_name ~ r.classname_pattern)
            AND (r.name_pattern IS NULL OR m.method_name ~ r.name_pattern)
        """

        private const val CANDIDATE_METHODS_CTE = """
            WITH candidate_methods AS (
                SELECT
                    m.*,
                    bm.probe_start_pos AS probe_start_pos,
                    EXISTS (
                        SELECT 1
                        FROM raw_data.method_ignore_rules r
                        WHERE r.group_id = m.group_id
                            AND r.app_id = m.app_id
                            AND $SAVED_RULE_MATCH
                    ) AS ignored,
                    ARRAY(
                        SELECT r.id
                        FROM raw_data.method_ignore_rules r
                        WHERE r.group_id = m.group_id
                            AND r.app_id = m.app_id
                            AND $SAVED_RULE_MATCH
                        ORDER BY r.id
                    ) AS matching_rule_ids
                FROM raw_data.build_methods bm
                JOIN raw_data.methods m
                    ON m.group_id = bm.group_id
                    AND m.app_id = bm.app_id
                    AND m.method_id = bm.method_id
                WHERE bm.group_id = ?
                    AND bm.app_id = ?
                    AND bm.build_id = ?
            )
        """
    }
}
