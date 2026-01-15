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
import com.epam.drill.admin.etl.EtlExtractingResult
import com.epam.drill.admin.etl.EtlPipeline
import com.epam.drill.admin.etl.EtlProcessingResult
import com.epam.drill.admin.etl.EtlLoadingResult
import com.epam.drill.admin.etl.EtlRow
import com.epam.drill.admin.etl.EtlStatus
import com.epam.drill.admin.etl.flow.CompletableSharedFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.time.Instant
import kotlin.system.measureTimeMillis

class EtlPipelineImpl<T: EtlRow>(
    override val name: String,
    override val extractor: DataExtractor<T>,
    override val loaders: List<DataLoader<T>>,
    private val bufferSize: Int = 2000
) : EtlPipeline<T> {
    private val logger = KotlinLogging.logger {}

    override suspend fun execute(
        groupId: String,
        sinceTimestampPerLoader: Map<String, Instant>,
        untilTimestamp: Instant,
        onExtractingProgress: suspend (EtlExtractingResult) -> Unit,
        onLoadingProgress: suspend (String, EtlLoadingResult) -> Unit,
        onStatusChanged: suspend (loaderName: String, status: EtlStatus) -> Unit
    ): EtlProcessingResult = withContext(Dispatchers.IO) {
        val minProcessedTime = sinceTimestampPerLoader.values.min()
        logger.debug { "ETL pipeline [$name] starting for group [$groupId] since $minProcessedTime..." }
        var results = EtlLoadingResult(lastProcessedAt = minProcessedTime)
        val duration = measureTimeMillis {
            results = processEtl(
                groupId,
                minProcessedTime,
                sinceTimestampPerLoader,
                untilTimestamp,
                onExtractingProgress,
                onLoadingProgress,
                onStatusChanged
            )
        }
        logger.debug {
            if (results.processedRows == 0L && !results.isFailed) {
                "ETL pipeline [$name] for group [$groupId] completed in ${duration}ms, no new rows"
            } else {
                val errors = results.errorMessage?.let { ", errors: $it" } ?: ""
                "ETL pipeline [$name] for group [$groupId] completed in ${duration}ms, rows processed: ${results.processedRows}" + errors
            }
        }
        EtlProcessingResult(
            groupId = groupId,
            pipelineName = name,
            lastProcessedAt = results.lastProcessedAt,
            rowsProcessed = results.processedRows,
            errorMessage = results.errorMessage,
            status = if (results.isFailed) EtlStatus.FAILED else EtlStatus.SUCCESS
        )
    }

    override suspend fun cleanUp(groupId: String) {
        loaders.forEach { it.deleteAll(groupId) }
    }

    private suspend fun CoroutineScope.processEtl(
        groupId: String,
        extractorSinceTimestamp: Instant,
        sinceTimestampPerLoader: Map<String, Instant>,
        untilTimestamp: Instant,
        onExtractingProgress: suspend (EtlExtractingResult) -> Unit,
        onLoadingProgress: suspend (loaderName: String, EtlLoadingResult) -> Unit,
        onStatusChanged: suspend (loaderName: String, status: EtlStatus) -> Unit
    ): EtlLoadingResult {
        val flow = CompletableSharedFlow<T>(
            replay = 0,
            extraBufferCapacity = bufferSize
        )
        return loaders.map { loader ->
            async {
                loadData(
                    groupId,
                    loader,
                    sinceTimestampPerLoader[loader.name] ?: extractorSinceTimestamp,
                    untilTimestamp,
                    flow,
                    onLoadingProgress,
                    onStatusChanged
                )
            }
        }.also { jobs ->
            extractData(groupId, flow, jobs, extractorSinceTimestamp, untilTimestamp, onExtractingProgress)
        }.awaitAll().max()
    }

    private suspend fun extractData(
        groupId: String,
        flow: CompletableSharedFlow<T>,
        jobs: List<Deferred<EtlLoadingResult>>,
        sinceTimestamp: Instant,
        untilTimestamp: Instant,
        onExtractingProgress: suspend (EtlExtractingResult) -> Unit,
    ) {
        try {
            // Start extractor only after all jobs are ready to consume data otherwise data may be lost
            flow.waitForSubscribers(jobs.count { it.isActive })
            extractor.extract(groupId, sinceTimestamp, untilTimestamp, flow, onExtractingProgress)
        } catch (e: Throwable) {
            logger.debug(e) { "ETL pipeline [$name] failed for extractor [${extractor.name}]: ${e.message}" }
            onExtractingProgress(
                EtlExtractingResult(
                    errorMessage = "Error during extracting data with extractor ${extractor.name}: ${e.message ?: e.javaClass.simpleName}",
                )
            )
        } finally {
            // Complete the flow to signal jobs that extraction is done
            flow.complete()
        }
    }

    private suspend fun loadData(
        groupId: String,
        loader: DataLoader<T>,
        sinceTimestamp: Instant,
        untilTimestamp: Instant,
        flow: Flow<T>,
        onLoadingProgress: suspend (loaderNmae: String, result: EtlLoadingResult) -> Unit,
        onStatusChanged: suspend (loaderName: String, status: EtlStatus) -> Unit
    ): EtlLoadingResult = try {
        loader.load(groupId, sinceTimestamp, untilTimestamp, flow,
            onLoadingProgress =  { onLoadingProgress(loader.name, it) },
            onStatusChanged = { onStatusChanged(loader.name, it) }
        )
    } catch (e: Throwable) {
        logger.debug(e) { "ETL pipeline [$name] failed for loader [${loader.name}]: ${e.message}" }
        EtlLoadingResult(
            errorMessage = "Error during loading data with loader ${loader.name}: ${e.message ?: e.javaClass.simpleName}",
            lastProcessedAt = sinceTimestamp
        ).also { onLoadingProgress(loader.name, it) }
    }
}