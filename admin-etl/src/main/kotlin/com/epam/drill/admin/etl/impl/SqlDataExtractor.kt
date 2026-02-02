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

import com.epam.drill.admin.etl.EtlRow
import com.epam.drill.admin.etl.UntypedRow
import kotlinx.coroutines.Dispatchers
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
    override val extractionLimit: Int,
    open val sqlQuery: String,
    open val database: Database,
    open val fetchSize: Int,
    open val loggingFrequency: Int,
) : PageDataExtractor<T>(name, extractionLimit, loggingFrequency) {
    private val logger = KotlinLogging.logger {}

    override suspend fun extractPage(
        groupId: String,
        sinceTimestamp: Instant,
        untilTimestamp: Instant,
        limit: Int,
        onExtractionExecuted: suspend (Long) -> Unit,
        rowsExtractor: suspend (T) -> Unit
    ) {
        val preparedSql = prepareSql(sqlQuery)

        execSuspend(
            sql = preparedSql.getSql(),
            args = preparedSql.getArgs(
                UntypedRow(sinceTimestamp, mapOf(
                    "group_id" to groupId,
                    "since_timestamp" to java.sql.Timestamp.from(sinceTimestamp),
                    "until_timestamp" to java.sql.Timestamp.from(untilTimestamp),
                    "limit" to limit,
                ))
            ),
        ) { rs, duration ->
            val meta = rs.metaData
            val columnCount = meta.columnCount

            onExtractionExecuted(duration)
            while (rs.next()) {
                val row = parseRow(rs, meta, columnCount)
                rowsExtractor(row)
            }
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



