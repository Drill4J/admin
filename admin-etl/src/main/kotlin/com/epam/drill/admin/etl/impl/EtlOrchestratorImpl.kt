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

import com.epam.drill.admin.etl.EtlMetadata
import com.epam.drill.admin.etl.EtlMetadataRepository
import com.epam.drill.admin.etl.EtlOrchestrator
import com.epam.drill.admin.etl.EtlPipeline
import com.epam.drill.admin.etl.EtlProcessingResult
import com.epam.drill.admin.etl.EtlStatus
import mu.KotlinLogging
import java.time.Instant
import kotlin.system.measureTimeMillis

open class EtlOrchestratorImpl(
    open val pipelines: List<EtlPipeline<*>>,
    open val metadataRepository: EtlMetadataRepository,
    open val batchSize: Int = 100
) : EtlOrchestrator {
    private val logger = KotlinLogging.logger {}

    override suspend fun runAll(): List<EtlProcessingResult> {
        logger.debug("ETL started...")
        val results = mutableListOf<EtlProcessingResult>()
        val duration = measureTimeMillis {
            for (pipeline in pipelines) {
                val result = run(pipeline)
                results.add(result)
            }
        }
        logger.info {
            val rowsProcessed = results.sumOf { it.rowsProcessed }
            val failures = results.count { !it.success }
            if (rowsProcessed == 0 && failures == 0)
                "ETL completed in ${duration}ms, no new rows"
            else
                "ETL completed in ${duration}ms, rows processed: $rowsProcessed, failures: $failures"
        }
        return results
    }

    override suspend fun run(pipeline: EtlPipeline<*>): EtlProcessingResult {
        val snapshotTime = Instant.now()
        val metadataList = metadataRepository.getAllMetadataByExtractor(pipeline.name, pipeline.extractor.name)
        val lastProcessedTime = findMinimumProcessedRowTimestamp(metadataList, pipeline.loaders.map { it.name }.toSet())
        try {
            val pipelineResult = pipeline.execute(
                sinceTimestamp = lastProcessedTime,
                untilTimestamp = snapshotTime,
                batchSize = batchSize,
                onLoadCompleted = { loaderName, result ->
                    try {
                        val oldMetadata = metadataList.find { it.loaderName == loaderName }
                        metadataRepository.saveMetadata(
                            EtlMetadata(
                                pipelineName = pipeline.name,
                                extractorName = pipeline.extractor.name,
                                loaderName = loaderName,
                                status = if (result.success) EtlStatus.SUCCESS else EtlStatus.FAILURE,
                                lastProcessedAt = result.lastProcessedAt ?: lastProcessedTime,
                                errorMessage = result.errorMessage,
                                lastRunAt = snapshotTime,
                                duration = ((oldMetadata?.duration ?: 0L) + (result.duration ?: 0L)),
                                rowsProcessed = ((oldMetadata?.rowsProcessed ?: 0) + result.processedRows)
                            )
                        )
                    } catch (e: Throwable) {
                        logger.error("ETL pipeline [${pipeline.name}] failed to update metadata: ${e.message}", e)
                    }
                }
            )
            return pipelineResult
        } catch (e: Throwable) {
            logger.error("ETL pipeline [${pipeline.name}] failed: ${e.message}", e)
            return EtlProcessingResult(
                pipelineName = pipeline.name,
                lastProcessedAt = lastProcessedTime,
                rowsProcessed = 0,
                success = false,
                errorMessage = e.message
            )
        }
    }

    private fun findMinimumProcessedRowTimestamp(
        metadataList: List<EtlMetadata>,
        loaderNames: Set<String>
    ): Instant {
        // If there is a new loader that has no metadata yet
        if (!loaderNames.all { it in metadataList.map(EtlMetadata::loaderName) })
            return Instant.EPOCH
        // Find the minimum lastProcessedAt among the specified loaders to ensure all loaders have processed up to that point
        return metadataList
            .filter { it.loaderName in loaderNames }
            .minByOrNull { it.lastProcessedAt }
            ?.lastProcessedAt
            ?: Instant.EPOCH
    }
}

