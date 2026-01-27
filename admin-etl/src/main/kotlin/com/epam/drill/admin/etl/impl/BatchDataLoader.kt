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
import com.epam.drill.admin.etl.EtlRow
import com.epam.drill.admin.etl.EtlStatus
import com.epam.drill.admin.etl.flow.StoppableFlow
import com.epam.drill.admin.etl.flow.stoppable
import kotlinx.coroutines.flow.Flow
import mu.KotlinLogging
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.seconds

abstract class BatchDataLoader<T : EtlRow>(
    override val name: String,
    open val batchSize: Int = 1000,
    open val loggingFrequency: Int = 10,
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
        onLoadingProgress: suspend (EtlLoadingResult) -> Unit,
        onStatusChanged: suspend (EtlStatus) -> Unit
    ): EtlLoadingResult {
        var result = EtlLoadingResult(lastProcessedAt = sinceTimestamp)
        val flow = collector.stoppable()
        val batchNo = AtomicInteger(0)
        val loadedRows = AtomicLong(0)
        val skippedRows = AtomicLong(0)
        val buffer = mutableListOf<T>()
        var lastLoadedTimestamp: Instant = sinceTimestamp
        var previousTimestamp: Instant? = null
        suspend fun <T> StoppableFlow<T>.stopWithMessage(message: String) {
            stop()
            result += EtlLoadingResult(
                errorMessage = message,
                lastProcessedAt = lastLoadedTimestamp
            ).also {
                onLoadingProgress(it)
            }
        }

        trackProgressOf {
            flow.collect { row ->
                if (loadedRows.get() == 0L && skippedRows.get() == 0L) {
                    logger.debug { "ETL loader [$name] for group [$groupId] loading rows..." }
                    onStatusChanged(EtlStatus.LOADING)
                }
                val currentTimestamp = row.timestamp
                if (previousTimestamp != null && currentTimestamp < previousTimestamp) {
                    flow.stopWithMessage("Timestamps in the extracted data are not in ascending order: $currentTimestamp < $previousTimestamp")
                    return@collect
                }

                // Skip rows that are already processed
                if (currentTimestamp <= sinceTimestamp) {
                    previousTimestamp = currentTimestamp
                    skippedRows.incrementAndGet()
                    return@collect
                }

                if (currentTimestamp > untilTimestamp) {
                    flow.stop()
                    return@collect
                }

                // If timestamp changed and buffer is full, flush the buffer
                if (previousTimestamp != null && currentTimestamp != previousTimestamp && buffer.size >= batchSize) {
                    result += flushBuffer(groupId, buffer, batchNo) { batch ->
                        if (batch.success) {
                            lastLoadedTimestamp =
                                previousTimestamp ?: throw IllegalStateException("Previous timestamp is null")
                        }
                        EtlLoadingResult(
                            errorMessage = if (!batch.success) result.errorMessage else null,
                            lastProcessedAt = lastLoadedTimestamp,
                            processedRows = if (batch.success) batch.rowsLoaded else 0L,
                            duration = batch.duration
                        ).also {
                            onLoadingProgress(it)
                        }
                    }
                }

                if (result.isFailed) {
                    flow.stop()
                    return@collect
                }

                // Skip rows that are not processable
                if (!isProcessable(row)) {
                    previousTimestamp = currentTimestamp
                    skippedRows.incrementAndGet()
                    // If timestamp changed and there are a lot of skipped rows, update progress
                    if (previousTimestamp != null && currentTimestamp != previousTimestamp && buffer.isEmpty() && skippedRows.get() % batchSize == 0L) {
                        onLoadingProgress(
                            EtlLoadingResult(
                                lastProcessedAt = previousTimestamp ?: throw IllegalStateException("Previous timestamp is null"),
                                processedRows = 0,
                            )
                        )
                    }
                    return@collect
                }

                buffer += row
                previousTimestamp = currentTimestamp
                loadedRows.incrementAndGet()
            }
        }.every(loggingFrequency.seconds) {
            if (loadedRows.get() > 0L || skippedRows.get() > 0L) {
                logger.debug {
                    "ETL loader [$name] for group [$groupId] loaded ${loadedRows.get()} rows" +
                            ", batch: ${batchNo.get()}" +
                            ", skipped rows: ${skippedRows.get()}"
                }
            }

        }

        if (!result.isFailed) {
            if (buffer.isNotEmpty()) {
                // Commit any remaining rows in the buffer
                result += flushBuffer(groupId, buffer, batchNo) { batch ->
                    if (batch.success) {
                        lastLoadedTimestamp = untilTimestamp
                    }
                    EtlLoadingResult(
                        errorMessage = if (!batch.success) batch.errorMessage else null,
                        lastProcessedAt = lastLoadedTimestamp,
                        processedRows = if (batch.success) batch.rowsLoaded else 0,
                        duration = batch.duration
                    ).also {
                        onLoadingProgress(it)
                    }
                }
            } else {
                // Update last processed timestamp even if no rows were left in the buffer
                result += EtlLoadingResult(
                    lastProcessedAt = untilTimestamp
                ).also {
                    onLoadingProgress(it)
                }
            }
            onStatusChanged(EtlStatus.SUCCESS)
        }
        logger.debug {
            val errors = result.errorMessage?.let { ", errors: $it" } ?: ""
            "ETL loader [$name] for group [$groupId] complete loading for ${result.processedRows} rows" + errors
        }
        return result
    }

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
        logger.trace { "ETL loader [$name] for group [$groupId] loaded ${it.processedRows} rows in ${it.duration ?: 0}ms, batch: $batchNo" }
    }
}