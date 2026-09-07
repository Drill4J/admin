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
package com.epam.drill.admin.metrics.config

import com.epam.drill.admin.common.config.DatabaseConfig
import com.epam.drill.admin.common.config.executeQueryReturnMap as commonExecuteQueryReturnMap
import com.epam.drill.admin.common.config.executeUpdate as commonExecuteUpdate
import org.jetbrains.exposed.sql.Transaction

object MetricsDatabaseConfig : DatabaseConfig(
    dbSchema = "metrics",
    schemaMigrationLocation = "classpath:metrics/db/migration"
)

fun Transaction.executeQueryReturnMap(sqlQuery: String, vararg params: Any?): List<Map<String, Any?>> =
    commonExecuteQueryReturnMap(sqlQuery, *params)

fun Transaction.executeUpdate(sql: String, vararg params: Any?) =
    commonExecuteUpdate(sql, *params)

fun Transaction.executeQueryReturnMap(buildSql: SqlBuilder.() -> Unit): List<Map<String, Any?>> {
    val builder = SqlBuilderImpl().apply { buildSql() }
    return executeQueryReturnMap(builder.sqlQuery.toString(), *builder.params.toTypedArray())
}

fun fromResource(resourcePath: String): String {
    return MetricsDatabaseConfig::class.java.getResource(resourcePath)?.readText()
        ?: throw IllegalArgumentException("Resource not found: $resourcePath")
}
