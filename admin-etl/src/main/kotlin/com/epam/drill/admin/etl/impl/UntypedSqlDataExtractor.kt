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

import com.epam.drill.admin.etl.UntypedRow
import org.jetbrains.exposed.sql.Database
import org.postgresql.util.PGobject
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.time.Instant
import java.util.Date

class UntypedSqlDataExtractor(
    name: String,
    sqlQuery: String,
    database: Database,
    fetchSize: Int = 2000,
    extractionLimit: Int = 1_000_000,
    private val lastExtractedAtColumnName: String,
) : SqlDataExtractor<UntypedRow>(name, extractionLimit, sqlQuery, database, fetchSize) {

    override fun prepareSql(sql: String): PreparedSql<UntypedRow> {
        return UntypedPreparedSql.prepareSql(sql)
    }

    override fun parseRow(rs: ResultSet, meta: ResultSetMetaData, columnCount: Int): UntypedRow {
        val row = mutableMapOf<String, Any?>()
        for (i in 1..columnCount) {
            val columnName = meta.getColumnName(i)
            val value = when (meta.getColumnTypeName(i)) {
                "bit", "varbit" -> {
                    val bit = rs.getObject(i)
                    when (bit) {
                        is Boolean -> PGobject().apply {
                            type = "varbit"
                            value = if (bit) "1" else "0"
                        }
                        is String -> PGobject().apply {
                            type = "varbit"
                            value = bit
                        }
                        is PGobject -> bit
                        else -> throw IllegalStateException("Unsupported BIT/VARBIT type: ${bit?.javaClass?.name}")
                    }
                }
                else -> rs.getObject(i)
            }
            row[columnName] = value
        }
        return UntypedRow(getLastExtractedTimestamp(row), row)
    }

    private fun getLastExtractedTimestamp(args: Map<String, Any?>): Instant {
        val timestamp = args[lastExtractedAtColumnName]
        return when (timestamp) {
            is Instant -> timestamp
            is Date -> timestamp.toInstant()
            is String -> Instant.parse(timestamp)
            else -> throw IllegalStateException("Could not extract timestamp column $lastExtractedAtColumnName from row: $args")
        }
    }
}