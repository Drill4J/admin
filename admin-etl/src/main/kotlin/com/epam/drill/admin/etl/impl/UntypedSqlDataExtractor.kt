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

import org.jetbrains.exposed.sql.Database
import org.postgresql.util.PGobject
import java.sql.ResultSet
import java.sql.ResultSetMetaData

class UntypedSqlDataExtractor(
    name: String,
    sqlQuery: String,
    database: Database
) : SqlDataExtractor<Map<String, Any?>>(name, sqlQuery, database) {
    override fun parseRow(rs: ResultSet, meta: ResultSetMetaData, columnCount: Int): Map<String, Any?> {
        val row = mutableMapOf<String, Any?>()
        for (i in 1..columnCount) {
            val columnName = meta.getColumnName(i)
            val value = when (meta.getColumnTypeName(i)) {
                "bit", "varbit" -> {
                    val bit = rs.getObject(i)
                    when (bit) {
                        is Boolean -> if (bit) "1" else "0"
                        is String -> bit
                        is PGobject -> bit.value ?: ""
                        else -> throw IllegalStateException("Unsupported BIT/VARBIT type: ${bit?.javaClass?.name}")
                    }
                }
                "json" -> rs.getObject(i).toString().replace("'", "''")
                else -> rs.getObject(i)
            }
            row[columnName] = value
        }
        return row
    }
}