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

import com.epam.drill.admin.etl.iterator.FanOutSequence
import com.epam.drill.admin.etl.DataExtractor
import com.epam.drill.admin.etl.DataLoader
import com.epam.drill.admin.etl.EtlPipeline
import com.epam.drill.admin.etl.EtlProcessingResult
import com.epam.drill.admin.etl.LoadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.time.Instant
import kotlin.system.measureTimeMillis

open class EtlPipelineImpl<T>(
    override val name: String,
    override val extractor: DataExtractor<T>,
    override val loaders: List<DataLoader<T>>
) : EtlPipeline<T> {
    private val logger = KotlinLogging.logger {}

    override suspend fun execute(
        sinceTimestamp: Instant,
        untilTimestamp: Instant,
        batchSize: Int,
        onLoadCompleted: suspend (String, LoadResult) -> Unit
    ): EtlProcessingResult = withContext(Dispatchers.IO) {
        var results = LoadResult.EMPTY
        val duration = measureTimeMillis {
            logger.debug { "ETL pipeline [$name] started since $sinceTimestamp" }
            val data = extractor.extract(sinceTimestamp, untilTimestamp, batchSize)
            if (!data.hasNext()) return@measureTimeMillis
            val fanOut = FanOutSequence(data)
            results = loaders.associateWith { fanOut.iterator() }.map { (loader, iterator) ->
                async {
                    try {
                        loader.load(iterator, batchSize).also { result ->
                            onLoadCompleted(loader.name, result)
                        }
                    } catch (e: Exception) {
                        logger.debug(e) { "ETL pipeline [$name] failed for loader [${loader.name}]: ${e.message}" }
                        LoadResult(
                            success = false,
                            errorMessage = "Error during loading data with loader ${loader.name}: ${e.message ?: e.javaClass.simpleName}"
                        ).also { onLoadCompleted(loader.name, it) }
                    }
                }
            }.awaitAll().min()
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
}