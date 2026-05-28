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
import com.epam.drill.admin.etl.EtlExtractingResult
import com.epam.drill.admin.etl.EtlLoadingResult
import com.epam.drill.admin.etl.EtlMetadata
import com.epam.drill.admin.etl.EtlMetadataRepository
import com.epam.drill.admin.etl.EtlOrchestrator
import com.epam.drill.admin.etl.EtlPipeline
import com.epam.drill.admin.etl.EtlProcessingResult
import com.epam.drill.admin.etl.EtlRow
import com.epam.drill.admin.etl.EtlStatus
import com.epam.drill.admin.etl.flow.CompletableSharedFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.time.Instant
import java.util.Collections
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.minutes

open class EtlOrchestratorImpl(
    override val name: String,
    open val pipelines: List<EtlPipeline<*, *>>,
    open val metadataRepository: EtlMetadataRepository,
    open val consistencyWindow: Long = 0,
    open val processingDelay: Long = 0,
    open val bufferSize: Int = 2000,
) : EtlOrchestrator {
    private val logger = KotlinLogging.logger {}

    override suspend fun run(groupId: String, initTimestamp: Instant): List<EtlProcessingResult> =
        withContext(Dispatchers.IO) {
            logger.info("ETL [$name] for group [$groupId] is starting...")
            val results = Collections.synchronizedList(mutableListOf<EtlProcessingResult>())
            val duration = measureTimeMillis {
                trackProgressOf {
                    // Group pipelines by extractor name; pipelines in the same group share one extractor run
                    val extractorGroups = pipelines.groupBy { it.extractor.name }
                    extractorGroups.map { (_, groupedPipelines) ->
                        async {
                            @Suppress("UNCHECKED_CAST")
                            val typedPipelines = groupedPipelines as List<EtlPipeline<EtlRow, *>>
                            results += runPipelineGroupByExtractor(
                                groupId = groupId,
                                groupedPipelines = typedPipelines,
                                extractor = typedPipelines.first().extractor,
                                initTimestamp = initTimestamp
                            )
                        }
                    }.awaitAll()
                }.every(1.minutes) {
                    logger.info { "ETL [$name] for group [$groupId] is still running..." }
                }
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

    override suspend fun rerun(
        groupId: String,
        initTimestamp: Instant,
        withDataDeletion: Boolean,
    ): List<EtlProcessingResult> =
        withContext(Dispatchers.IO) {
            logger.info { "ETL [$name] for group [$groupId] is deleting all metadata for rerun..." }
            pipelines.map { it.name }.forEach { pipelineName ->
                metadataRepository.deleteMetadataByPipeline(groupId, pipelineName)
            }
            logger.info { "ETL [$name] for group [$groupId] deleted all metadata for rerun." }
            if (withDataDeletion) {
                logger.info { "ETL [$name] for group [$groupId] is deleting all data for rerun..." }
                pipelines.forEach { it.cleanUp(groupId) }
                logger.info { "ETL [$name] for group [$groupId] deleted all data for rerun." }
            }
            return@withContext run(groupId, initTimestamp)
        }

    /**
     * Runs a group of pipelines that share the same extractor.
     * The extractor is executed exactly once; its output is broadcast to all pipelines in the group.
     */
    private suspend fun <T: EtlRow> runPipelineGroupByExtractor(
        groupId: String,
        groupedPipelines: List<EtlPipeline<T, *>>,
        extractor: DataExtractor<T> = groupedPipelines.first().extractor,
        initTimestamp: Instant,
    ): List<EtlProcessingResult> = coroutineScope {
        val snapshotTime = Instant.now().minusSeconds(processingDelay)

        // Compute per-pipeline sinceTimestamp from metadata
        val sinceTimestamps: Map<String, Instant> = groupedPipelines.associate { pipeline ->
            val metadata = metadataRepository.getMetadata(
                groupId, pipeline.name, extractor.name, pipeline.loader.name
            )
            val sinceTimestamp = if (metadata?.lastProcessedAt != null)
                metadata.lastProcessedAt.minusSeconds(consistencyWindow)
            else
                initTimestamp
            pipeline.name to sinceTimestamp
        }

        for (pipeline in groupedPipelines) {
            try {
                metadataRepository.saveMetadata(
                    EtlMetadata(
                        groupId = groupId,
                        pipelineName = pipeline.name,
                        extractorName = extractor.name,
                        loaderName = pipeline.loader.name,
                        status = EtlStatus.EXTRACTING,
                        lastProcessedAt = sinceTimestamps[pipeline.name] ?: initTimestamp,
                        lastRunAt = snapshotTime,
                    )
                )
            } catch (e: Throwable) {
                logger.warn(
                    "ETL pipeline [${pipeline.name}] for group [$groupId] failed to save initial metadata: ${e.message}",
                    e
                )
            }
        }

        val minLastProcessedAt = sinceTimestamps.values.min()
        val sharedFlow = CompletableSharedFlow<T>(replay = 0, extraBufferCapacity = bufferSize)
        val jobs = groupedPipelines.map { pipeline ->
            async {
                runPipelineWithExtractionFlow(
                    groupId = groupId,
                    pipeline = pipeline,
                    sinceTimestamp = sinceTimestamps[pipeline.name] ?: initTimestamp,
                    untilTimestamp = snapshotTime,
                    sharedFlow = sharedFlow,
                )
            }
        }
        sharedFlow.waitForSubscribers(jobs.count { it.isActive })
        try {
            extractor.extract(
                groupId = groupId,
                sinceTimestamp = minLastProcessedAt,
                untilTimestamp = snapshotTime,
                emitter = sharedFlow,
                onExtractingProgress = { result ->
                    groupedPipelines.forEach { pipeline ->
                        progressExtracting(groupId, pipeline.name, extractor.name, result)
                    }
                }
            )
        } catch (e: Throwable) {
            logger.debug(e) { "ETL extractor [${extractor.name}] for group [$groupId] failed: ${e.message}" }
            val errorResult = EtlExtractingResult(
                errorMessage = "Error during extracting data with extractor ${extractor.name}: ${e.message ?: e.javaClass.simpleName}"
            )
            groupedPipelines.forEach { pipeline ->
                progressExtracting(groupId, pipeline.name, extractor.name, errorResult)
            }
        } finally {
            sharedFlow.complete()
        }

        jobs.awaitAll()
    }

    /**
     * Runs a single pipeline with the provided shared flow of extracted data.
     */
    private suspend fun <T : EtlRow, R : EtlRow> runPipelineWithExtractionFlow(
        groupId: String,
        pipeline: EtlPipeline<T, R>,
        sinceTimestamp: Instant,
        untilTimestamp: Instant,
        sharedFlow: Flow<T>,
    ): EtlProcessingResult = try {
        pipeline.execute(
            groupId = groupId,
            sinceTimestamp = sinceTimestamp,
            untilTimestamp = untilTimestamp,
            extractedFlow = sharedFlow,
            onLoadingProgress = { result ->
                progressLoading(groupId, pipeline.name, pipeline.extractor.name, pipeline.loader.name, result)
            },
            onStatusChanged = { status ->
                try {
                    metadataRepository.accumulateMetadataByLoader(
                        groupId = groupId,
                        pipelineName = pipeline.name,
                        extractorName = pipeline.extractor.name,
                        loaderName = pipeline.loader.name,
                        status = status,
                    )
                } catch (e: Throwable) {
                    logger.warn(
                        "ETL pipeline [${pipeline.name}] for group [$groupId] failed to update loading status: ${e.message}",
                        e
                    )
                }
            }
        )
    } catch (e: Throwable) {
        logger.error("ETL pipeline [${pipeline.name}] for group [$groupId] failed: ${e.message}", e)
        EtlProcessingResult(
            groupId = groupId,
            pipelineName = pipeline.name,
            lastProcessedAt = sinceTimestamp,
            rowsProcessed = 0,
            status = EtlStatus.FAILED,
            errorMessage = e.message,
        )
    }

    private suspend fun progressExtracting(
        groupId: String,
        pipelineName: String,
        extractorName: String,
        result: EtlExtractingResult,
    ) {
        try {
            metadataRepository.accumulateMetadataByExtractor(
                groupId = groupId,
                pipelineName = pipelineName,
                extractorName = extractorName,
                errorMessage = result.errorMessage,
                extractDuration = result.duration,
            )
        } catch (e: Throwable) {
            logger.warn(
                "ETL pipeline [$pipelineName] for group [$groupId] failed to update extracting progress: ${e.message}",
                e
            )
        }
    }

    private suspend fun progressLoading(
        groupId: String,
        pipelineName: String,
        extractorName: String,
        loaderName: String,
        result: EtlLoadingResult,
    ) {
        try {
            metadataRepository.accumulateMetadataByLoader(
                groupId = groupId,
                pipelineName = pipelineName,
                extractorName = extractorName,
                loaderName = loaderName,
                errorMessage = result.errorMessage,
                lastProcessedAt = result.lastProcessedAt,
                loadDuration = result.duration ?: 0L,
                rowsProcessed = result.processedRows,
            )
        } catch (e: Throwable) {
            logger.warn(
                "ETL pipeline [$pipelineName] for group [$groupId] failed to update loading progress: ${e.message}", e
            )
        }
    }
}

