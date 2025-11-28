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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.time.Instant
import java.util.Collections
import kotlin.system.measureTimeMillis

open class EtlOrchestratorImpl(
    override val name: String,
    open val pipelines: List<EtlPipeline<*>>,
    open val metadataRepository: EtlMetadataRepository,
) : EtlOrchestrator {
    private val logger = KotlinLogging.logger {}

    override suspend fun run(initTimestamp: Instant): List<EtlProcessingResult> = withContext(Dispatchers.IO) {
        logger.info("ETL [$name] starting with init timestamp $initTimestamp...")
        val results = Collections.synchronizedList(mutableListOf<EtlProcessingResult>())
        val duration = measureTimeMillis {
            pipelines.map { pipeline ->
                async {
                    results += runPipeline(pipeline, initTimestamp)
                }
            }.awaitAll()
        }
        logger.info {
            val rowsProcessed = results.sumOf { it.rowsProcessed }
            val failures = results.count { it.status == EtlStatus.FAILED }
            if (rowsProcessed == 0 && failures == 0)
                "ETL [$name] completed in ${duration}ms, no new rows"
            else
                "ETL [$name] completed in ${duration}ms, rows processed: $rowsProcessed, failures: $failures"
        }
        return@withContext results
    }

    override suspend fun rerun(initTimestamp: Instant, withDataDeletion: Boolean): List<EtlProcessingResult> = withContext(Dispatchers.IO) {
        logger.info { "ETL [$name] deleting all metadata for rerun." }
        pipelines.map { it.name }.forEach { pipelineName ->
            metadataRepository.deleteMetadataByPipeline(pipelineName)
        }
        logger.info { "ETL [$name] deleted all metadata for rerun." }
        if (withDataDeletion) {
            logger.info { "ETL [$name] deleting all data for rerun." }
            pipelines.forEach { it.cleanUp() }
            logger.info { "ETL [$name] deleted all data for rerun." }
        }
        val results = run(initTimestamp)
        return@withContext results
    }

    private suspend fun runPipeline(pipeline: EtlPipeline<*>, initTimestamp: Instant): EtlProcessingResult {
        val snapshotTime = Instant.now()
        val metadata = metadataRepository.getAllMetadataByExtractor(pipeline.name, pipeline.extractor.name).associate {
            it.loaderName to it
        }
        val loaderNames = pipeline.loaders.map { it.name }.toSet()
        val timestampPerLoader = loaderNames.associateWith { (metadata[it]?.lastProcessedAt ?: initTimestamp) }

        try {
            val pipelineResult = pipeline.execute(
                sinceTimestampPerLoader = timestampPerLoader,
                untilTimestamp = snapshotTime
            ) { loaderName, result ->
                try {
                    metadataRepository.saveMetadata(
                        EtlMetadata(
                            pipelineName = pipeline.name,
                            extractorName = pipeline.extractor.name,
                            loaderName = loaderName,
                            status = result.status,
                            lastProcessedAt = result.lastProcessedAt,
                            errorMessage = result.errorMessage,
                            lastRunAt = snapshotTime,
                            duration = result.duration ?: 0L,
                            rowsProcessed = result.processedRows
                        )
                    )
                } catch (e: Throwable) {
                    logger.warn("ETL pipeline [${pipeline.name}] failed to update metadata: ${e.message}", e)
                }
            }
            return pipelineResult
        } catch (e: Throwable) {
            logger.error("ETL pipeline [${pipeline.name}] failed: ${e.message}", e)
            return EtlProcessingResult(
                pipelineName = pipeline.name,
                lastProcessedAt = initTimestamp,
                rowsProcessed = 0,
                status = EtlStatus.FAILED,
                errorMessage = e.message
            )
        }
    }
}

