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
import com.epam.drill.admin.etl.EtlLoadingResult
import com.epam.drill.admin.etl.EtlStatus
import com.epam.drill.admin.etl.flow.StoppableFlow
import com.epam.drill.admin.etl.flow.stoppable
import kotlinx.coroutines.flow.Flow
import mu.KotlinLogging
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

abstract class BatchDataLoader<T>(
    override val name: String,
    open val batchSize: Int = 1000
) : DataLoader<T> {
    private val logger = KotlinLogging.logger {}

    class BatchResult(
        val success: Boolean,
        val rowsLoaded: Long,
        val duration: Long? = null,
        val errorMessage: String? = null
    )

    override suspend fun load(
        groupId: String,
        sinceTimestamp: Instant,
        untilTimestamp: Instant,
        collector: Flow<T>,
        onLoadCompleted: suspend (EtlLoadingResult) -> Unit
    ): EtlLoadingResult {
        var result = EtlLoadingResult(status = EtlStatus.LOADING, lastProcessedAt = sinceTimestamp)
        val flow = collector.stoppable()
        val batchNo = AtomicInteger(0)
        val buffer = mutableListOf<T>()
        var lastLoadedTimestamp: Instant = sinceTimestamp
        var previousTimestamp: Instant? = null
        suspend fun <T> StoppableFlow<T>.stopWithMessage(message: String) {
            stop()
            result += EtlLoadingResult(
                status = EtlStatus.FAILED,
                errorMessage = message,
                lastProcessedAt = lastLoadedTimestamp
            ).also {
                onLoadCompleted(it)
            }
        }
        logger.debug { "ETL loader [$name] loading rows..." }

        flow.collect { row ->
            val currentTimestamp = getLastExtractedTimestamp(row)
            if (currentTimestamp == null) {
                flow.stopWithMessage("Could not extract timestamp from the data row: $row")
                return@collect
            }

            if (previousTimestamp != null && currentTimestamp < previousTimestamp) {
                flow.stopWithMessage("Timestamps in the extracted data are not in ascending order: $currentTimestamp < $previousTimestamp")
                return@collect
            }

            // Skip rows that are already processed
            if (currentTimestamp <= sinceTimestamp) {
                previousTimestamp = currentTimestamp
                return@collect
            }

            if (currentTimestamp > untilTimestamp) {
                flow.stop()
                return@collect
            }

            // Skip rows that are not processable
            if (!isProcessable(row)) {
                previousTimestamp = currentTimestamp
                return@collect
            }

            // If timestamp changed and buffer is full, flush the buffer
            if (previousTimestamp != null && currentTimestamp != previousTimestamp) {
                if (buffer.size >= batchSize) {
                    result += flushBuffer(groupId, buffer, batchNo) { batch ->
                        if (batch.success) {
                            lastLoadedTimestamp = previousTimestamp ?: throw IllegalStateException("Previous timestamp is null")
                        }
                        EtlLoadingResult(
                            status = if (batch.success) EtlStatus.LOADING else EtlStatus.FAILED,
                            errorMessage = if (!batch.success) result.errorMessage else null,
                            lastProcessedAt = lastLoadedTimestamp,
                            processedRows = if (batch.success) batch.rowsLoaded else 0L,
                            duration = batch.duration
                        ).also {
                            onLoadCompleted(it)
                        }
                    }
                }
            }

            if (result.isFailed) {
                flow.stop()
                return@collect
            }

            buffer += row
            previousTimestamp = currentTimestamp
        }

        if (!result.isFailed) {
            if (buffer.isNotEmpty()) {
                // Commit any remaining rows in the buffer
                result += flushBuffer(groupId, buffer, batchNo) { batch ->
                    if (batch.success) {
                        lastLoadedTimestamp = previousTimestamp ?: throw IllegalStateException("Previous timestamp is null")
                    }
                    EtlLoadingResult(
                        status = if (batch.success) EtlStatus.SUCCESS else EtlStatus.FAILED,
                        errorMessage = if (!batch.success) result.errorMessage else null,
                        lastProcessedAt = lastLoadedTimestamp,
                        processedRows = if (batch.success) batch.rowsLoaded else 0,
                        duration = batch.duration
                    ).also {
                        onLoadCompleted(it)
                    }
                }
            } else {
                // Finalize with success
                lastLoadedTimestamp = previousTimestamp ?: sinceTimestamp
                result += EtlLoadingResult(
                    status = EtlStatus.SUCCESS,
                    lastProcessedAt = lastLoadedTimestamp
                ).also {
                    onLoadCompleted(it)
                }
            }
        }
        logger.debug { "ETL loader [$name] complete loading for ${result.processedRows} rows, status: ${result.status}" }
        return result
    }

    abstract fun getLastExtractedTimestamp(args: T): Instant?
    abstract fun isProcessable(args: T): Boolean
    abstract suspend fun loadBatch(groupId: String, batch: List<T>, batchNo: Int): BatchResult

    private suspend fun flushBuffer(
        groupId: String,
        buffer: MutableList<T>,
        batchNo: AtomicInteger,
        onBatchCompleted: suspend (BatchResult) -> EtlLoadingResult
    ): EtlLoadingResult = loadBatch(
        groupId,
        buffer,
        batchNo.incrementAndGet()
    ).let {
        buffer.clear()
        onBatchCompleted(it)
    }.also {
        logger.trace { "ETL loader [$name] loaded ${it.processedRows} rows in ${it.duration ?: 0}ms, batch: $batchNo" }
    }
}