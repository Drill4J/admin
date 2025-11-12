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
import com.epam.drill.admin.etl.EtlPipeline
import com.epam.drill.admin.etl.EtlProcessingResult
import com.epam.drill.admin.etl.EtlLoadingResult
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

class EtlPipelineImpl<T>(
    override val name: String,
    override val extractor: DataExtractor<T>,
    override val loaders: List<DataLoader<T>>,
    private val bufferSize: Int = 1000
) : EtlPipeline<T> {
    private val logger = KotlinLogging.logger {}

    override suspend fun execute(
        sinceTimestamp: Instant,
        untilTimestamp: Instant,
        onLoadCompleted: suspend (String, EtlLoadingResult) -> Unit
    ): EtlProcessingResult = withContext(Dispatchers.IO) {
        logger.debug { "ETL pipeline [$name] starting since $sinceTimestamp..." }
        var results = EtlLoadingResult.EMPTY
        val duration = measureTimeMillis {
            results = processEtl(sinceTimestamp, untilTimestamp, onLoadCompleted)
        }
        logger.debug {
            if (results.processedRows == 0 && results.success) {
                "ETL pipeline [$name] completed in ${duration}ms, no new rows"
            } else
                "ETL pipeline [$name] completed in ${duration}ms, rows processed: ${results.processedRows}, success: ${results.success}"
        }
        EtlProcessingResult(
            pipelineName = name,
            lastProcessedAt = results.lastProcessedAt ?: sinceTimestamp,
            rowsProcessed = results.processedRows,
            success = results.success,
            errorMessage = results.errorMessage
        )
    }

    private suspend fun CoroutineScope.processEtl(
        sinceTimestamp: Instant,
        untilTimestamp: Instant,
        onLoadCompleted: suspend (String, EtlLoadingResult) -> Unit
    ): EtlLoadingResult {
        val flow = CompletableSharedFlow<T>(
            replay = 0,
            extraBufferCapacity = bufferSize
        )
        return loaders.map { loader ->
            async {
                loadData(loader, sinceTimestamp, untilTimestamp, flow, onLoadCompleted)
            }
        }.also { jobs ->
            extractData(flow, jobs, sinceTimestamp, untilTimestamp)
        }.awaitAll().min()
    }

    private suspend fun extractData(
        flow: CompletableSharedFlow<T>,
        jobs: List<Deferred<EtlLoadingResult>>,
        sinceTimestamp: Instant,
        untilTimestamp: Instant
    ) {
        try {
            // Start extractor only after all jobs are ready to consume data otherwise data may be lost
            flow.waitForSubscribers(jobs.count { it.isActive })
            extractor.extract(sinceTimestamp, untilTimestamp, flow)
        } finally {
            // Complete the flow to signal jobs that extraction is done
            flow.complete()
        }
    }

    private suspend fun loadData(
        loader: DataLoader<T>,
        sinceTimestamp: Instant,
        untilTimestamp: Instant,
        flow: Flow<T>,
        onLoadCompleted: suspend (String, EtlLoadingResult) -> Unit
    ): EtlLoadingResult = try {
        loader.load(sinceTimestamp, untilTimestamp, flow) { onLoadCompleted(loader.name, it) }
    } catch (e: Exception) {
        logger.debug(e) { "ETL pipeline [$name] failed for loader [${loader.name}]: ${e.message}" }
        EtlLoadingResult(
            success = false,
            errorMessage = "Error during loading data with loader ${loader.name}: ${e.message ?: e.javaClass.simpleName}"
        ).also { onLoadCompleted(loader.name, it) }
    }
}