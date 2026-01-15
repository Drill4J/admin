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

import com.epam.drill.admin.etl.DataExtractor
import com.epam.drill.admin.etl.EtlExtractingResult
import com.epam.drill.admin.etl.EtlRow
import com.epam.drill.admin.etl.UntypedRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.FlowCollector
import mu.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.time.Instant
import kotlin.system.measureTimeMillis
import kotlin.use

abstract class SqlDataExtractor<T : EtlRow>(
    override val name: String,
    open val sqlQuery: String,
    open val database: Database,
    open val fetchSize: Int,
    open val extractionLimit: Int,
) : DataExtractor<T> {
    private val logger = KotlinLogging.logger {}

    override suspend fun extract(
        groupId: String,
        sinceTimestamp: Instant,
        untilTimestamp: Instant,
        emitter: FlowCollector<T>,
        onExtractingProgress: suspend (EtlExtractingResult) -> Unit
    ) {
        val preparedSql = prepareSql(sqlQuery)
        var currentSince = sinceTimestamp
        var page = 0
        var hasMore = true
        val buffer: MutableList<T> = mutableListOf()

        try {
            while (hasMore && currentSince < untilTimestamp) {
                page++

                val params = mapOf(
                    "group_id" to groupId,
                    "since_timestamp" to java.sql.Timestamp.from(currentSince),
                    "until_timestamp" to java.sql.Timestamp.from(untilTimestamp),
                    "limit" to extractionLimit,
                )

                logger.debug { "ETL extractor [$name] executing query for page $page..." }
                execSuspend(
                    sql = preparedSql.getSql(),
                    args = preparedSql.getArgs(
                        UntypedRow(currentSince, params)
                    ),
                ) { rs, duration ->
                    logger.debug { "ETL extractor [$name] executed query for page $page in ${duration}ms " }
                    onExtractingProgress(
                        EtlExtractingResult(
                            duration = duration
                        )
                    )

                    val meta = rs.metaData
                    val columnCount = meta.columnCount
                    var previousTimestamp: Instant? = null
                    var previousEmittedTimestamp: Instant? = null
                    var pageRows = 0L

                    while (rs.next()) {
                        pageRows++

                        val row = parseRow(rs, meta, columnCount)
                        val currentTimestamp = row.timestamp

                        if (previousTimestamp != null && currentTimestamp < previousTimestamp) {
                            throw IllegalStateException("Timestamps in the extracted data are not in ascending order: $currentTimestamp < $previousTimestamp")
                        }
                        buffer.add(row)

                        if (previousTimestamp != null && currentTimestamp != previousTimestamp) {
                            // Emit buffered rows when timestamp changes
                            emitBuffer(buffer, emitter)
                            previousEmittedTimestamp = previousTimestamp
                        }

                        previousTimestamp = currentTimestamp
                    }

                    if (pageRows == 0L || pageRows < extractionLimit) {
                        hasMore = false
                        emitBuffer(buffer, emitter)
                        logger.debug { "ETL extractor [$name] completed fetching, total pages: $page, last extracted at $currentSince" }
                    } else {
                        currentSince = previousEmittedTimestamp
                            ?: throw IllegalStateException("No rows were emitted on page $page because all fetched records had the same timestamp. " +
                                    "Please increase the extraction limit. Current is $extractionLimit.")
                        hasMore = true
                        logger.debug { "ETL extractor [$name] fetched $pageRows rows on page $page, last extracted at $currentSince" }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error {
                "Error during data extraction with extractor [$name]: ${e.message ?: e.javaClass.simpleName}"
            }
            onExtractingProgress(
                EtlExtractingResult(
                    errorMessage = e.message
                )
            )
        }
    }

    private suspend fun emitBuffer(
        buffer: MutableList<T>,
        emitter: FlowCollector<T>
    ) {
        if (buffer.isEmpty()) return;
        for (bufferedRow in buffer) {
            emitter.emit(bufferedRow)
        }
        buffer.clear()
    }


    private suspend fun execSuspend(
        sql: String,
        args: List<Any?>,
        collect: suspend (ResultSet, Long) -> Unit
    ) {
        newSuspendedTransaction(context = Dispatchers.IO, db = database) {
            connection.autoCommit = false
            connection.readOnly = true
            val stmt = connection.prepareStatement(sql, false)
            try {
                stmt.fetchSize = fetchSize

                val columns = args
                for (index in columns.indices) {
                    val value = columns[index]
                    if (value != null) {
                        stmt.set(index + 1, value)
                    } else
                        stmt.setNull(index + 1, TextColumnType())
                }

                val resultSet: ResultSet
                val duration = measureTimeMillis {
                    resultSet = stmt.executeQuery()
                }
                resultSet.use { rs ->
                    collect(rs, duration)
                }
            } finally {
                stmt.closeIfPossible()
            }
        }
    }

    abstract fun parseRow(rs: ResultSet, meta: ResultSetMetaData, columnCount: Int): T
    abstract fun prepareSql(sql: String): PreparedSql<UntypedRow>
}



