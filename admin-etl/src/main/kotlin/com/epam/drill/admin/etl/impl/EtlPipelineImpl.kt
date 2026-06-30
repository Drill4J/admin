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
import com.epam.drill.admin.etl.DataLoader
import com.epam.drill.admin.etl.DataTransformer
import com.epam.drill.admin.etl.EtlPipeline
import com.epam.drill.admin.etl.EtlProcessingResult
import com.epam.drill.admin.etl.EtlLoadingResult
import com.epam.drill.admin.etl.EtlContext
import com.epam.drill.admin.etl.EtlRow
import com.epam.drill.admin.etl.EtlStatus
import com.epam.drill.admin.etl.config.EtlMeter
import com.epam.drill.admin.etl.flow.ClosableFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.time.Instant
import kotlin.system.measureTimeMillis

class EtlPipelineImpl<T : EtlRow, R : EtlRow>(
    override val name: String,
    override val extractor: DataExtractor<T>,
    override val transformer: DataTransformer<T, R>,
    override val loader: DataLoader<R>,
    private val metrics: EtlMeter,
) : EtlPipeline<T, R> {
    private val logger = KotlinLogging.logger {}

    override suspend fun execute(
        context: EtlContext,
        sinceTimestamp: Instant,
        untilTimestamp: Instant,
        extractionFlow: ClosableFlow<T>,
        onLoadingProgress: suspend (EtlLoadingResult) -> Unit,
        onStatusChanged: suspend (EtlStatus) -> Unit,
    ): EtlProcessingResult = withContext(Dispatchers.IO) {
        val groupId = context.groupId
        logger.debug { "ETL pipeline [$name] for group [$groupId] loading since $sinceTimestamp..." }
        var result = EtlLoadingResult(lastProcessedAt = sinceTimestamp)
        val duration = measureTimeMillis {
            result = loadData(
                context,
                sinceTimestamp,
                untilTimestamp,
                extractionFlow,
                onLoadingProgress,
                onStatusChanged
            )
        }
        logger.debug {
            if (result.processedRows == 0L && !result.isFailed) {
                "ETL pipeline [$name] for group [$groupId] completed in ${duration}ms, no new rows"
            } else {
                val errors = result.errorMessage?.let { ", errors: $it" } ?: ""
                "ETL pipeline [$name] for group [$groupId] completed in ${duration}ms, rows processed: ${result.processedRows}" + errors
            }
        }
        EtlProcessingResult(
            context = context,
            pipelineName = name,
            lastProcessedAt = result.lastProcessedAt,
            rowsProcessed = result.processedRows,
            errorMessage = result.errorMessage,
            status = if (result.isFailed) EtlStatus.FAILED else EtlStatus.SUCCESS
        )
    }

    override suspend fun cleanUp(context: EtlContext) {
        loader.deleteAll(context)
    }

    private suspend fun loadData(
        context: EtlContext,
        sinceTimestamp: Instant,
        untilTimestamp: Instant,
        extractionFlow: ClosableFlow<T>,
        onLoadingProgress: suspend (EtlLoadingResult) -> Unit,
        onStatusChanged: suspend (EtlStatus) -> Unit,
    ): EtlLoadingResult = try {
        val transformationFlow = getTransformationFlow(
            context,
            sinceTimestamp,
            untilTimestamp,
            extractionFlow
        ) { errorMessage, lastProcessedAt ->
            onLoadingProgress(
                EtlLoadingResult(
                    errorMessage = errorMessage,
                    lastProcessedAt = lastProcessedAt
                )
            )
        }
        transformer.transform(context, transformationFlow).let { loadingFlow ->
            loader.load(
                context, sinceTimestamp, untilTimestamp, loadingFlow,
                onLoadingProgress = onLoadingProgress,
                onStatusChanged = onStatusChanged,
            )
        }
    } catch (e: Throwable) {
        val groupId = context.groupId
        logger.debug(e) { "ETL pipeline [$name] for group [$groupId] failed for loader [${loader.name}]: ${e.message}" }
        EtlLoadingResult(
            errorMessage = "Error during loading data with loader ${loader.name}: ${e.message ?: e.javaClass.simpleName}",
            lastProcessedAt = sinceTimestamp
        ).also { onLoadingProgress(it) }
    }

    private fun getTransformationFlow(
        context: EtlContext,
        sinceTimestamp: Instant,
        untilTimestamp: Instant,
        extractionFlow: ClosableFlow<T>,
        onLoadingError: suspend (String, Instant) -> Unit
    ): Flow<T> {
        var previousTimestamp: Instant? = null
        val rowsExtracted = metrics.rowsExtracted(name, context)
        val skippedRows = metrics.rowsSkipped(name, context)
        suspend fun <T> ClosableFlow<T>.closeWithMessage(message: String) {
            close()
            onLoadingError(message, previousTimestamp ?: sinceTimestamp)
        }
        return flow {
            extractionFlow.collect { row ->
                val currentTimestamp = row.timestamp
                rowsExtracted.increment()
                if (previousTimestamp != null && currentTimestamp < previousTimestamp) {
                    extractionFlow.closeWithMessage("Timestamps in the extracted data are not in ascending order: $currentTimestamp < $previousTimestamp")
                    return@collect
                }
                if (currentTimestamp > untilTimestamp) {
                    extractionFlow.close()
                    return@collect
                }
                // Skip rows that are already processed
                if (currentTimestamp <= sinceTimestamp) {
                    previousTimestamp = currentTimestamp
                    skippedRows.increment()
                    return@collect
                }
                emit(row)
                previousTimestamp = currentTimestamp
            }
        }
    }
}