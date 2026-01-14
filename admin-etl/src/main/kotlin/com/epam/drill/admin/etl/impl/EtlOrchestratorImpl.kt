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

    override suspend fun run(groupId: String, initTimestamp: Instant): List<EtlProcessingResult> = withContext(Dispatchers.IO) {
        logger.info("ETL [$name] starting for group [$groupId] with init timestamp $initTimestamp...")
        val results = Collections.synchronizedList(mutableListOf<EtlProcessingResult>())
        val duration = measureTimeMillis {
            pipelines.map { pipeline ->
                async {
                    results += runPipeline(groupId, pipeline, initTimestamp)
                }
            }.awaitAll()
        }
        logger.info {
            val rowsProcessed = results.sumOf { it.rowsProcessed }
            val failures = results.count { it.status == EtlStatus.FAILED }
            if (rowsProcessed == 0L && failures == 0)
                "ETL [$name] for group [$groupId] completed in ${duration}ms, no new rows"
            else
                "ETL [$name] for group [$groupId] completed in ${duration}ms, rows processed: $rowsProcessed, failures: $failures"
        }
        return@withContext results
    }

    override suspend fun rerun(groupId: String, initTimestamp: Instant, withDataDeletion: Boolean): List<EtlProcessingResult> =
        withContext(Dispatchers.IO) {
            logger.info { "ETL [$name] deleting all metadata for group [$groupId] for rerun." }
            pipelines.map { it.name }.forEach { pipelineName ->
                metadataRepository.deleteMetadataByPipeline(groupId, pipelineName)
            }
            logger.info { "ETL [$name] deleted all metadata for group [$groupId] for rerun." }
            if (withDataDeletion) {
                logger.info { "ETL [$name] deleting all data for group [$groupId] for rerun." }
                pipelines.forEach { it.cleanUp(groupId) }
                logger.info { "ETL [$name] deleted all data for group [$groupId] for rerun." }
            }
            val results = run(groupId, initTimestamp)
            return@withContext results
        }

    private suspend fun runPipeline(groupId: String, pipeline: EtlPipeline<*>, initTimestamp: Instant): EtlProcessingResult {
        val snapshotTime = Instant.now()
        val metadata = metadataRepository.getAllMetadataByExtractor(groupId, pipeline.name, pipeline.extractor.name)
            .associateBy { it.loaderName }
        val loaderNames = pipeline.loaders.map { it.name }.toSet()
        val timestampPerLoader = loaderNames.associateWith { (metadata[it]?.lastProcessedAt ?: initTimestamp) }

        try {
            for (loader in loaderNames) {
                metadataRepository.saveMetadata(
                    EtlMetadata(
                        groupId = groupId,
                        pipelineName = pipeline.name,
                        extractorName = pipeline.extractor.name,
                        loaderName = loader,
                        status = EtlStatus.EXTRACTING,
                        lastProcessedAt = timestampPerLoader[loader] ?: initTimestamp,
                        lastRunAt = snapshotTime,
                        lastDuration = 0L,
                        lastRowsProcessed = 0L,
                        errorMessage = null
                    )
                )
            }
            val pipelineResult = pipeline.execute(
                groupId = groupId,
                sinceTimestampPerLoader = timestampPerLoader,
                untilTimestamp = snapshotTime,
                onExtractCompleted = { result ->
                    try {
                        metadataRepository.accumulateMetadataDurationByExtractor(
                            groupId,
                            pipeline.name,
                            pipeline.extractor.name,
                            result.duration
                        )
                    } catch (e: Throwable) {
                        logger.warn("ETL pipeline [${pipeline.name}] failed to update metadata: ${e.message}", e)
                    }
                },
                onLoadCompleted = { loaderName, result ->
                    try {
                        metadataRepository.accumulateMetadata(
                            EtlMetadata(
                                groupId = groupId,
                                pipelineName = pipeline.name,
                                extractorName = pipeline.extractor.name,
                                loaderName = loaderName,
                                status = result.status,
                                lastProcessedAt = result.lastProcessedAt,
                                errorMessage = result.errorMessage,
                                lastRunAt = snapshotTime,
                                lastDuration = result.duration ?: 0L,
                                lastRowsProcessed = result.processedRows
                            )
                        )
                    } catch (e: Throwable) {
                        logger.warn("ETL pipeline [${pipeline.name}] failed to update metadata: ${e.message}", e)
                    }
                }
            )
            return pipelineResult
        } catch (e: Throwable) {
            logger.error("ETL pipeline [${pipeline.name}] failed: ${e.message}", e)
            return EtlProcessingResult(
                groupId = groupId,
                pipelineName = pipeline.name,
                lastProcessedAt = initTimestamp,
                rowsProcessed = 0,
                status = EtlStatus.FAILED,
                errorMessage = e.message
            )
        }
    }
}

