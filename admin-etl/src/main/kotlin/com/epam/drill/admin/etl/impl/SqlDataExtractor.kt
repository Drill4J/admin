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

import com.epam.drill.admin.metrics.etl.DataExtractor
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import mu.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.javatime.JavaInstantColumnType
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.postgresql.util.PGobject
import java.sql.ResultSet
import java.time.Instant
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.map
import kotlin.collections.mapValues

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
    ): Sequence<Map<String, Any?>> {
        //TODO : sqlQuery should be processed for named placeholders :sinceTimestamp and :untilTimestamp
        val args = listOf(
            JavaInstantColumnType() to sinceTimestamp,
            JavaInstantColumnType() to untilTimestamp
        )
        return object : Sequence<Map<String, Any?>> {
            override fun iterator(): Iterator<Map<String, Any?>> = SqlIterator()

            private inner class SqlIterator : Iterator<Map<String, Any?>> {
                private var currentBatch: List<Map<String, Any?>> = emptyList()
                private var currentIndex = 0
                private var offset = 0
                private var exhausted = false

                override fun hasNext(): Boolean {
                    // If we have items in current batch, return true
                    if (currentIndex < currentBatch.size) {
                        return true
                    }

                    // If we've exhausted all batches, return false
                    if (exhausted) {
                        return false
                    }

                    // Try to load next batch
                    loadNextBatch()

                    return currentBatch.isNotEmpty()
                }

                override fun next(): Map<String, Any?> {
                    if (!hasNext()) {
                        throw NoSuchElementException("No more elements in SqlSequence")
                    }

                    val item = currentBatch[currentIndex]
                    currentIndex++
                    return item
                }

                private fun loadNextBatch() {
                    currentBatch = runBlocking {
                        fetchBatch(offset, batchSize)
                    }

                    if (currentBatch.isEmpty() || currentBatch.size < batchSize) {
                        exhausted = true
                    }

                    currentIndex = 0
                    offset += currentBatch.size
                }

                private suspend fun fetchBatch(offset: Int, limit: Int): List<Map<String, Any?>> {
                    return newSuspendedTransaction(db = database) {
                        execSqlWithPagination(offset, limit)
                    }
                }

                private fun Transaction.execSqlWithPagination(offset: Int, limit: Int): List<Map<String, Any?>> {
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

            private fun JsonElement.jsonPrimitiveOrElement(): Any {
                return when (this) {
                    is JsonPrimitive -> this.contentOrNull ?: this.booleanOrNull ?: this.longOrNull ?: this.doubleOrNull ?: ""
                    is JsonObject -> this.mapValues { (_, value) -> value.jsonPrimitiveOrElement() }
                    is JsonArray -> this.map { it.jsonPrimitiveOrElement() }
                }
            }

        }
    }
}



