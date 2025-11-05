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
import com.epam.drill.admin.etl.LoadResult
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.flow.Flow
import mu.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

abstract class SqlDataLoader<T>(
    override val name: String,
    open val sqlUpsert: String,
    open val database: Database,
    open val batchSize: Int = 1000
) : DataLoader<T> {
    private val logger = KotlinLogging.logger {}

    override suspend fun load(
        sinceTimestamp: Instant,
        untilTimestamp: Instant,
        collector: Flow<T>,
        onLoadCompleted: suspend (LoadResult) -> Unit
    ): LoadResult {
        var result: LoadResult = LoadResult.EMPTY
        val batchNo = AtomicInteger(0)
        val buffer = mutableListOf<T>()

        collector.collect { row ->
            buffer.add(row)

            if (buffer.size >= batchSize) {
                result += execSqlInBatch(sqlUpsert, buffer, batchNo).also { onLoadCompleted(it) }
                if (!result.success) {
                    throw CancellationException("Loading cancelled due to previous errors: ${result.errorMessage}")
                }
                buffer.clear()
            }
        }
        if (result.success && buffer.isNotEmpty()) {
            result += execSqlInBatch(sqlUpsert, buffer, batchNo).also { onLoadCompleted(it) }
        }

        return result
    }

    abstract fun prepareSql(sql: String, args: T): String
    abstract fun getLastExtractedTimestamp(args: T): Instant?

    private suspend fun execSqlInBatch(
        sql: String,
        batch: List<T>,
        batchNo: AtomicInteger
    ): LoadResult {
        val stmts = mutableListOf<String>()
        var lastExtractedAt: Instant? = null
        batch.forEach { data ->
            val preparedSql = prepareSql(sql, data)
            lastExtractedAt = getLastExtractedTimestamp(data)
            if (lastExtractedAt == null) {
                return LoadResult(
                    processedRows = 0,
                    lastProcessedAt = null,
                    success = false,
                    errorMessage = "Error during loading data with loader $name: Missing timestamp column in the extracted data"
                )
            }
            stmts.add(preparedSql)
        }
        val duration = try {
            newSuspendedTransaction(db = database) {
                execInBatch(stmts)
                duration
            }.also { duration ->
                logger.debug { "ETL loader [$name] loaded ${stmts.size} rows in ${duration}ms, batch: ${batchNo.incrementAndGet()}" }
            }
        } catch (e: Exception) {
            return LoadResult(
                success = false,
                errorMessage = "Error during loading data with loader $name: ${e.message ?: e.javaClass.simpleName}"
            )
        }
        return LoadResult(
            success = true,
            lastProcessedAt = lastExtractedAt,
            processedRows = stmts.size,
            duration = duration
        )
    }
}