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

abstract class SqlDataExtractor<T>(
    override val name: String,
    open val sqlQuery: String,
    open val database: Database,
    open val fetchSize: Int,
) : DataExtractor<T> {
    private val logger = KotlinLogging.logger {}

    override suspend fun extract(
        groupId: String,
        sinceTimestamp: Instant,
        untilTimestamp: Instant,
        emitter: FlowCollector<T>,
        onExtractCompleted: suspend (EtlExtractingResult) -> Unit
    ) {
        val preparedSql = prepareSql(sqlQuery)
        val params = mapOf(
            "group_id" to groupId,
            "since_timestamp" to java.sql.Timestamp.from(sinceTimestamp),
            "until_timestamp" to java.sql.Timestamp.from(untilTimestamp)
        )
        execSuspend(
            sql = preparedSql.getSql(),
            args = preparedSql.getArgs(params),
        ) { rs, duration ->
            onExtractCompleted(EtlExtractingResult(success = true, duration = duration))
            collectInFlow(rs, emitter)
        }
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
                logger.debug { "ETL extractor [$name] extracting rows..." }
                val duration = measureTimeMillis {
                    resultSet = stmt.executeQuery()
                }
                logger.debug { "ETL extractor [$name] extracted rows in ${duration}ms" }
                resultSet.use { rs ->
                    collect(rs, duration)
                }
            } finally {
                stmt.closeIfPossible()
            }
        }
    }

    private suspend fun collectInFlow(rs: ResultSet, emitter: FlowCollector<T>) {
        val meta = rs.metaData
        val columnCount = rs.metaData.columnCount
        while (rs.next()) {
            emitter.emit(parseRow(rs, meta, columnCount))
        }
    }

    abstract fun parseRow(rs: ResultSet, meta: ResultSetMetaData, columnCount: Int): T
    abstract fun prepareSql(sql: String): PreparedSql<Map<String, Any?>>
}



