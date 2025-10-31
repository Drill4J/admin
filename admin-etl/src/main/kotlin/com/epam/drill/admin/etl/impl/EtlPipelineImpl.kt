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

import com.epam.drill.admin.etl.interator.FanOutSequence
import com.epam.drill.admin.etl.DataExtractor
import com.epam.drill.admin.etl.DataLoader
import com.epam.drill.admin.etl.EtlPipeline
import com.epam.drill.admin.etl.EtlProcessingResult
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
        batchSize: Int
    ): EtlProcessingResult = withContext(Dispatchers.IO) {
        var results = DataLoader.LoadResult.EMPTY
        val duration = measureTimeMillis {
            logger.debug { "ETL pipeline [$name] started since $sinceTimestamp" }
            val data = try {
                extractor.extract(sinceTimestamp, untilTimestamp, batchSize)
            } catch (e: Exception) {
                results += DataLoader.LoadResult(
                    success = false,
                    errorMessage = "Error during extraction data with extractor ${extractor.name}: ${e.message ?: e.javaClass.simpleName}"
                )
                logger.error(e) { "ETL pipeline [$name] failed for extractor [${extractor.name}]: ${e.message}" }
                return@measureTimeMillis
            }

            val fanOut = FanOutSequence(data)
            val loaderResults = loaders.associateWith { fanOut.iterator() }.map { (loader, iterator) ->
                async {
                    try {
                        loader.load(iterator, batchSize)
                    } catch (e: Exception) {
                        logger.error(e) { "ETL pipeline [$name] failed for loader [${loader.name}]: ${e.message}" }
                        DataLoader.LoadResult(
                            success = false,
                            errorMessage = "Error during loading data with loader ${loader.name}: ${e.message ?: e.javaClass.simpleName}"
                        )
                    }
                }
            }.awaitAll()

            loaderResults.forEach { result ->
                results += result
            }
        }
        logger.info {
            if (results.processedRows == 0) {
                "ETL pipeline [$name] completed in ${duration}ms, no new rows"
            } else
                "ETL pipeline [$name] completed in ${duration}ms, rows processed: ${results.processedRows}, failures: ${!results.success}"
        }
        EtlProcessingResult(
            pipelineName = name,
            lastProcessedAt = results.lastProcessedAt ?: sinceTimestamp,
            rowsProcessed = results.processedRows,
            success = results.success,
            errorMessage = results.errorMessage,
            duration = duration
        )
    }
}