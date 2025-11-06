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
package com.epam.drill.admin.etl.impl

import com.epam.drill.admin.etl.DataLoader
import org.jetbrains.exposed.sql.Database
import java.time.Instant
import java.util.Date

class UntypedSqlDataLoader(
    name: String,
    sql: String,
    database: Database,
    private val lastExtractedAtColumnName: String,
    batchSize: Int = 1000
) : SqlDataLoader<Map<String, Any?>>(name, batchSize, sql, database) {
    override fun prepareSql(
        sql: String,
        args: Map<String, Any?>
    ): String {
        var preparedSql = sql
        args.forEach {
            val value = when (val v = it.value) {
                null -> "NULL"
                is String -> "'${v.replace("'", "''")}'"
                is Instant -> "'$v'"
                is Date -> "'$v'"
                else -> "'$v'"
            }
            preparedSql = preparedSql.replace(":${it.key}", value)
        }
        return preparedSql
    }

    override fun getLastExtractedTimestamp(args: Map<String, Any?>): Instant? {
        val timestamp = args[lastExtractedAtColumnName]
        return when (timestamp) {
            is Instant -> timestamp
            is Date -> timestamp.toInstant()
            is String -> Instant.parse(timestamp)
            else -> null
        }
    }
}