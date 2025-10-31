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

import com.epam.drill.admin.etl.interator.BatchIterator
import com.epam.drill.admin.etl.DataExtractor
import mu.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.javatime.JavaInstantColumnType
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.transaction
import org.postgresql.util.PGobject
import java.sql.ResultSet
import java.time.Instant
import kotlin.collections.plus

open class SqlDataExtractor(
    override val name: String,
    open val sqlQuery: String,
    open val database: Database
) : DataExtractor<Map<String, Any?>> {
    private val logger = KotlinLogging.logger {}

    override suspend fun extract(
        sinceTimestamp: Instant,
        untilTimestamp: Instant,
        batchSize: Int
    ): Iterator<Map<String, Any?>> {
        //TODO : sqlQuery should be processed for named placeholders :sinceTimestamp and :untilTimestamp
        val args = listOf(
            JavaInstantColumnType() to sinceTimestamp,
            JavaInstantColumnType() to untilTimestamp
        )
        return object : BatchIterator<Map<String, Any?>>(batchSize) {
            override fun fetchBatch(
                offset: Int,
                batchSize: Int
            ): List<Map<String, Any?>> {
                return transaction(db = database) {
                    execSqlWithPagination(args, offset, batchSize)
                }
            }
        }
    }

    private fun Transaction.execSqlWithPagination(
        args: List<Pair<JavaInstantColumnType, Instant>>,
        offset: Int,
        limit: Int
    ): List<Map<String, Any?>> {
        // Append OFFSET and LIMIT to the SQL query
        val paginatedQuery = "$sqlQuery OFFSET ? LIMIT ?"
        val paginatedArgs = args + listOf(
            IntegerColumnType() to offset,
            IntegerColumnType() to limit
        )
        val result = exec(
            stmt = paginatedQuery,
            args = paginatedArgs,
            explicitStatementType = StatementType.SELECT
        ) { rs ->
            parseResultSet(rs)
        } ?: emptyList()
        logger.debug { "ETL [$name] extracted ${result.size} rows, offset $offset" }
        return result
    }

    private fun parseResultSet(rs: ResultSet): List<Map<String, Any?>> {
        val meta = rs.metaData
        val columnCount = meta.columnCount
        val results = mutableListOf<Map<String, Any?>>()

        while (rs.next()) {
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

                    else -> rs.getObject(i)
                }
                row[columnName] = value
            }
            results.add(row)
        }

        return results
    }
}



