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
import com.epam.drill.admin.etl.EtlContext
import com.epam.drill.admin.etl.EtlRow
import com.epam.drill.admin.etl.EtlStatus
import com.epam.drill.admin.etl.config.EtlMeter
import kotlinx.coroutines.flow.Flow
import mu.KotlinLogging
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

abstract class BatchDataLoader<T : EtlRow>(
    override val name: String,
    open val batchSize: Int = 1000,
    open val loggingFrequency: Int = 10,
    open val metrics: EtlMeter
) : DataLoader<T> {
    private val logger = KotlinLogging.logger {}

    class BatchResult(
        val success: Boolean,
        val rowsLoaded: Long,
        val duration: Long? = null,
        val errorMessage: String? = null
    )

    override suspend fun load(
        context: EtlContext,
        sinceTimestamp: Instant,
        untilTimestamp: Instant,
        collector: Flow<T>,
        onLoadingProgress: suspend (EtlLoadingResult) -> Unit,
        onStatusChanged: suspend (EtlStatus) -> Unit
    ): EtlLoadingResult {
        val groupId = context.groupId
        var result = EtlLoadingResult(lastProcessedAt = sinceTimestamp)
        val batchNo = AtomicInteger(0)
        val processedRows = metrics.rowsProcessed(name, context)
        val loadedRows = metrics.rowsLoaded(name, context)
        var isLoadingStarted = false
        val buffer = mutableListOf<T>()
        var lastLoadedTimestamp: Instant = sinceTimestamp
        var previousTimestamp: Instant? = null

        trackProgressOf {
            try {
                collector.collect { row ->
                    if (!isLoadingStarted) {
                        logger.debug { "ETL loader [$name] for group [$groupId] loading rows..." }
                        onStatusChanged(EtlStatus.LOADING)
                        isLoadingStarted = true
                    }
                    processedRows.increment()
                    val currentTimestamp = row.timestamp

                    // If timestamp changed and buffer is full, flush the buffer
                    if (previousTimestamp != null && currentTimestamp != previousTimestamp && buffer.size >= batchSize) {
                        result += flushBuffer(context, buffer, batchNo) { batch ->
                            if (batch.success) {
                                lastLoadedTimestamp =
                                    previousTimestamp ?: throw IllegalStateException("Previous timestamp is null")
                                loadedRows.increment(batch.rowsLoaded.toDouble())
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
                        throw IllegalStateException("ETL loading failed for [$groupId]: ${result.errorMessage}")
                    }

                    buffer += row
                    previousTimestamp = currentTimestamp
                }
            } catch (e: Throwable) {
                logger.debug(e) { "ETL loader [$name] for group [$groupId] failed while loading: ${e.message}" }
                result += EtlLoadingResult(
                    errorMessage = "Error during loading data with loader $name: ${e.message ?: e.javaClass.simpleName}",
                    lastProcessedAt = lastLoadedTimestamp
                ).also { onLoadingProgress(it) }
            }
        }.every(loggingFrequency.seconds) {
            if (isLoadingStarted) {
                logger.debug {
                    "ETL loader [$name] for group [$groupId] loaded ${loadedRows.count().toLong()} rows" +
                            ", batch: ${batchNo.get()}"
                }
            }

        }

        if (!result.isFailed) {
            if (buffer.isNotEmpty()) {
                // Commit any remaining rows in the buffer
                result += flushBuffer(context, buffer, batchNo) { batch ->
                    if (batch.success) {
                        lastLoadedTimestamp = previousTimestamp
                            ?: throw IllegalStateException("Previous timestamp is null")
                        loadedRows.increment(batch.rowsLoaded.toDouble())
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
                // Update last processed timestamp even if no rows were loaded
                if (lastLoadedTimestamp == sinceTimestamp) {
                    result += EtlLoadingResult(
                        lastProcessedAt = untilTimestamp
                    ).also {
                        onLoadingProgress(it)
                    }
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

    abstract suspend fun loadBatch(context: EtlContext, batch: List<T>, batchNo: Int): BatchResult

    private suspend fun flushBuffer(
        context: EtlContext,
        buffer: MutableList<T>,
        batchNo: AtomicInteger,
        onBatchCompleted: suspend (BatchResult) -> EtlLoadingResult
    ): EtlLoadingResult = loadBatch(
        context,
        buffer,
        batchNo.incrementAndGet()
    ).let {
        buffer.clear()
        onBatchCompleted(it)
    }.also {
        val groupId = context.groupId
        logger.trace { "ETL loader [$name] for group [$groupId] loaded ${it.processedRows} rows in ${it.duration ?: 0}ms, batch: $batchNo" }
    }
}