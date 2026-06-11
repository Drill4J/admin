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
import com.epam.drill.admin.etl.EtlContext
import com.epam.drill.admin.etl.EtlPipeline
import com.epam.drill.admin.etl.EtlProcessingResult
import com.epam.drill.admin.etl.EtlRow
import com.epam.drill.admin.etl.EtlStatus
import com.epam.drill.admin.etl.flow.ClosableFlow
import com.epam.drill.admin.etl.flow.SubscribableChannelFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.time.Instant
import java.util.Collections
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.minutes

open class EtlOrchestratorImpl(
    override val name: String,
    override val pipelines: List<EtlPipeline<*, *>>,
    open val metadataRepository: EtlMetadataRepository,
    open val consistencyWindow: Long = 0,
    open val processingDelay: Long = 0,
    open val bufferSize: Int = 2000,
) : EtlOrchestrator {
    private val logger = KotlinLogging.logger {}

    override suspend fun run(context: EtlContext, initTimestamp: Instant, finalTimestamp: Instant?): List<EtlProcessingResult> =
        withContext(Dispatchers.IO) {
            val groupId = context.groupId
            val snapshotTime = finalTimestamp ?: Instant.now().minusSeconds(processingDelay)
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
                                context = context,
                                groupedPipelines = typedPipelines,
                                extractor = typedPipelines.first().extractor,
                                initTimestamp = initTimestamp,
                                snapshotTime = snapshotTime,
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
        context: EtlContext,
        initTimestamp: Instant,
        finalTimestamp: Instant?,
        withDataDeletion: Boolean
    ): List<EtlProcessingResult> =
        withContext(Dispatchers.IO) {
            val groupId = context.groupId
            logger.info { "ETL [$name] for group [$groupId] is deleting all metadata for rerun..." }
            pipelines.map { it.name }.forEach { pipelineName ->
                metadataRepository.deleteMetadataByPipeline(context, pipelineName)
            }
            logger.info { "ETL [$name] for group [$groupId] deleted all metadata for rerun." }
            if (withDataDeletion) {
                logger.info { "ETL [$name] for group [$groupId] is deleting all data for rerun..." }
                pipelines.forEach { it.cleanUp(context) }
                logger.info { "ETL [$name] for group [$groupId] deleted all data for rerun." }
            }
            return@withContext run(context, initTimestamp, finalTimestamp)
        }

    /**
     * Runs a group of pipelines that share the same extractor.
     * The extractor is executed exactly once; its output is broadcast to all pipelines in the group.
     */
    private suspend fun <T: EtlRow> runPipelineGroupByExtractor(
        context: EtlContext,
        groupedPipelines: List<EtlPipeline<T, *>>,
        extractor: DataExtractor<T> = groupedPipelines.first().extractor,
        initTimestamp: Instant,
        snapshotTime: Instant,
    ): List<EtlProcessingResult> = coroutineScope {
        val groupId = context.groupId

        // Compute per-pipeline sinceTimestamp from metadata
        val sinceTimestamps: Map<String, Instant> = groupedPipelines.associate { pipeline ->
            val metadata = metadataRepository.getMetadata(context, pipeline.name)
            val sinceTimestamp = if (metadata?.lastProcessedAt != null)
                metadata.lastProcessedAt.minusSeconds(consistencyWindow)
            else
                initTimestamp
            pipeline.name to sinceTimestamp
        }

        val (skippedPipelines, activePipelines) = groupedPipelines.partition { pipeline ->
            (sinceTimestamps[pipeline.name] ?: initTimestamp) >= snapshotTime
        }

        val skippedResults = skippedPipelines.map { pipeline ->
            EtlProcessingResult(
                context = context,
                pipelineName = pipeline.name,
                lastProcessedAt = sinceTimestamps[pipeline.name] ?: initTimestamp,
                rowsProcessed = 0,
                status = EtlStatus.SKIPPED,
                errorMessage = null,
            ).also {
                logger.info { "ETL pipeline [${pipeline.name}] for group [$groupId] is already up-to-date." }
            }
        }

        if (activePipelines.isEmpty()) return@coroutineScope skippedResults

        for (pipeline in activePipelines) {
            try {
                metadataRepository.saveMetadata(
                    context,
                    EtlMetadata(
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

        val minLastProcessedAt = activePipelines.mapNotNull { sinceTimestamps[it.name] }.min()
        val sharedFlow = SubscribableChannelFlow<T>(bufferSize)
        val jobs = activePipelines.map { pipeline ->
            async {
                runPipelineWithExtractionFlow(
                    context = context,
                    pipeline = pipeline,
                    sinceTimestamp = sinceTimestamps[pipeline.name] ?: initTimestamp,
                    untilTimestamp = snapshotTime,
                    sharedFlow = sharedFlow.subscribe(),
                )
            }
        }
        sharedFlow.waitForSubscribers(jobs.count { it.isActive })
        extractRowsToExtractionFlow(
            context = context,
            extractor = extractor,
            sinceTimestamp = minLastProcessedAt,
            untilTimestamp = snapshotTime,
            sharedFlow = sharedFlow,
            activePipelines = activePipelines,
        )

        skippedResults + jobs.awaitAll()
    }

    private suspend fun <T : EtlRow> extractRowsToExtractionFlow(
        context: EtlContext,
        extractor: DataExtractor<T>,
        sinceTimestamp: Instant,
        untilTimestamp: Instant,
        sharedFlow: SubscribableChannelFlow<T>,
        activePipelines: List<EtlPipeline<T, *>>
    ) {
        try {
            extractor.extract(
                context = context,
                sinceTimestamp = sinceTimestamp,
                untilTimestamp = untilTimestamp,
                emitter = sharedFlow,
                onExtractingProgress = { result ->
                    activePipelines.forEach { pipeline ->
                        progressExtracting(context, pipeline.name, extractor.name, result)
                    }
                }
            )
        } catch (e: Throwable) {
            logger.debug(e) { "ETL extractor [${extractor.name}] for group [${context.groupId}] failed: ${e.message}" }
            sharedFlow.close(e)
        } finally {
            sharedFlow.close()
        }
    }

    /**
     * Runs a single pipeline with the provided shared flow of extracted data.
     */
    private suspend fun <T : EtlRow, R : EtlRow> runPipelineWithExtractionFlow(
        context: EtlContext,
        pipeline: EtlPipeline<T, R>,
        sinceTimestamp: Instant,
        untilTimestamp: Instant,
        sharedFlow: ClosableFlow<T>,
    ): EtlProcessingResult {
        val groupId = context.groupId
        return try {
            pipeline.execute(
                context = context,
                sinceTimestamp = sinceTimestamp,
                untilTimestamp = untilTimestamp,
                extractionFlow = sharedFlow,
                onLoadingProgress = { result ->
                    progressLoading(context, pipeline.name, result)
                },
                onStatusChanged = { status ->
                    try {
                        metadataRepository.accumulateMetadataByLoader(
                            context = context,
                            pipelineName = pipeline.name,
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
                context = context,
                pipelineName = pipeline.name,
                lastProcessedAt = sinceTimestamp,
                rowsProcessed = 0,
                status = EtlStatus.FAILED,
                errorMessage = e.message,
            )
        }
    }

    private suspend fun progressExtracting(
        context: EtlContext,
        pipelineName: String,
        extractorName: String,
        result: EtlExtractingResult,
    ) {
        try {
            metadataRepository.accumulateMetadataByExtractor(
                context = context,
                pipelineName = pipelineName,
                errorMessage = result.errorMessage,
                extractDuration = result.duration,
            )
        } catch (e: Throwable) {
            logger.warn(
                "ETL pipeline [$pipelineName] for group [${context.groupId}] failed to update extracting progress: ${e.message}",
                e
            )
        }
    }

    private suspend fun progressLoading(
        context: EtlContext,
        pipelineName: String,
        result: EtlLoadingResult,
    ) {
        try {
            metadataRepository.accumulateMetadataByLoader(
                context = context,
                pipelineName = pipelineName,
                errorMessage = result.errorMessage,
                lastProcessedAt = result.lastProcessedAt,
                loadDuration = result.duration ?: 0L,
                rowsProcessed = result.processedRows,
            )
        } catch (e: Throwable) {
            logger.warn(
                "ETL pipeline [$pipelineName] for group [${context.groupId}] failed to update loading progress: ${e.message}", e
            )
        }
    }
}
