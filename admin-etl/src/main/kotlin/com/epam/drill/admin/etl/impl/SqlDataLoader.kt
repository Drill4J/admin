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

import com.epam.drill.admin.metrics.etl.DataLoader
import mu.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Timestamp
import java.time.Instant
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

open class SqlDataLoader(
    override val name: String,
    open val sqlUpsert: String,
    open val database: Database,
    open val lastExtractedAtColumnName: String,
) : DataLoader<Map<String, Any?>> {
    private val logger = KotlinLogging.logger {}

    override suspend fun load(
        data: Sequence<Map<String, Any?>>,
        batchSize: Int
    ): DataLoader.LoadResult {
        val iterator = data.iterator()
        val batchParams = mutableListOf<Map<String, Any?>>()
        val batchNo = AtomicInteger(0)
        var result: DataLoader.LoadResult = DataLoader.LoadResult.EMPTY

        while (iterator.hasNext()) {
            val params = try {
                iterator.next()
            } catch (e: Exception) {
                return result + DataLoader.LoadResult(
                    success = false,
                    errorMessage = "Error during extraction data for loader $name: ${e.message ?: e.javaClass.simpleName}"
                )
            }
            batchParams.add(params)

            if (batchParams.size >= batchSize) {
                result += execSqlInBatch(sqlUpsert, batchParams, batchNo)
                if (!result.success) {
                    return result
                }
                batchParams.clear()
            }
        }
        if (batchParams.isNotEmpty()) {
            result += execSqlInBatch(sqlUpsert, batchParams, batchNo)
        }
        return result
    }

    private suspend fun execSqlInBatch(sqlUpsert: String, batchParams: List<Map<String, Any?>>, batchNo: AtomicInteger): DataLoader.LoadResult {
        val stmts = mutableListOf<String>()
        var lastExtractedAt: Instant? = null
        batchParams.forEach { params ->
            var sql = sqlUpsert
            params.forEach {
                val value = when (val v = it.value) {
                    null -> "NULL"
                    is String -> "'${v.replace("'", "''")}'"
                    is Instant -> "'$v'"
                    is Date -> "'$v'"
                    else -> "'$v'"
                }
                sql = sql.replace(":${it.key}", value)
            }
            lastExtractedAt = (params[lastExtractedAtColumnName] as? Timestamp)?.toInstant()
            if (lastExtractedAt == null) {
                return DataLoader.LoadResult(
                    processedRows = 0,
                    lastProcessedAt = null,
                    success = false,
                    errorMessage = "Error during loading data with loader $name: Missing or invalid '$lastExtractedAtColumnName' value in params $params"
                )
            }
            stmts.add(sql)
        }
        try {
            newSuspendedTransaction(db = database) {
                execInBatch(stmts)
            }
            batchNo.incrementAndGet()
        } catch (e: Exception) {
            return DataLoader.LoadResult(
                processedRows = 0,
                lastProcessedAt = null,
                success = false,
                errorMessage = "Error during loading data with loader $name: ${e.message ?: e.javaClass.simpleName}"
            )
        }
        logger.debug { "ETL [$name] loaded ${stmts.size} rows, batch: ${batchNo.get()}" }
        return DataLoader.LoadResult(
            processedRows = stmts.size,
            lastProcessedAt = lastExtractedAt,
            success = true
        )
    }
}