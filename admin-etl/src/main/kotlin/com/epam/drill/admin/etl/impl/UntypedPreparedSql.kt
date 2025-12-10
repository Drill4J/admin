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

class UntypedPreparedSql(val preparedSql: String, val indexes: List<String>) : PreparedSql<Map<String, Any?>> {
    override fun getSql() = preparedSql
    override fun getArgs(row: Map<String, Any?>): List<Any?> {
        return indexes.map { row[it] }
    }

    companion object {
        fun prepareSql(sql: String): PreparedSql<Map<String, Any?>> {
            val regex = Regex("""(?<!:):([a-zA-Z_][a-zA-Z0-9_]*)(?![:=])""")

            val indexes = mutableListOf<String>()

            val prepared = regex.replace(sql) { match ->
                val name = match.groupValues[1]
                indexes += name
                "?"
            }

            return UntypedPreparedSql(prepared, indexes)
        }
    }
}