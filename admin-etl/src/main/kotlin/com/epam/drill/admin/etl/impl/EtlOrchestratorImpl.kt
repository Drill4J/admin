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

import com.epam.drill.admin.etl.EtlExtractingResult
import com.epam.drill.admin.etl.EtlLoadingResult
import com.epam.drill.admin.etl.EtlMetadata
import com.epam.drill.admin.etl.EtlMetadataRepository
import com.epam.drill.admin.etl.EtlOrchestrator
import com.epam.drill.admin.etl.EtlPipeline
import com.epam.drill.admin.etl.EtlProcessingResult
import com.epam.drill.admin.etl.EtlRow
import com.epam.drill.admin.etl.EtlStatus
import io.ktor.util.pipeline.Pipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
) : EtlOrchestrator {
    private val logger = KotlinLogging.logger {}

    override suspend fun run(groupId: String, initTimestamp: Instant): List<EtlProcessingResult> =
        withContext(Dispatchers.IO) {
            logger.info("ETL [$name] for group [$groupId] is starting with init timestamp $initTimestamp...")
            val results = Collections.synchronizedList(mutableListOf<EtlProcessingResult>())
            val duration = measureTimeMillis {
                trackProgressOf {
                    pipelines.map { pipeline ->
                        async {
                            results += runPipeline(groupId, pipeline, initTimestamp)
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
        withDataDeletion: Boolean
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
            val results = run(groupId, initTimestamp)
            return@withContext results
        }

    private suspend fun runPipeline(
        groupId: String,
        pipeline: EtlPipeline<*, *>,
        initTimestamp: Instant
    ): EtlProcessingResult = coroutineScope {
        val snapshotTime = Instant.now()
        val metadata = metadataRepository.getAllMetadataByExtractor(groupId, pipeline.name, pipeline.extractor.name)
            .associateBy { it.loaderName }
        val loaderNames = pipeline.loaders.map { it.second.name }.toSet()
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
                    )
                )
            }
            val pipelineResult = pipeline.execute(
                groupId = groupId,
                sinceTimestampPerLoader = timestampPerLoader,
                untilTimestamp = snapshotTime,
                onExtractingProgress = { result ->
                    progressExtracting(
                        groupId = groupId,
                        pipelineName = pipeline.name,
                        extractorName = pipeline.extractor.name,
                        result = result
                    )
                },
                onLoadingProgress = { loaderName, result ->
                    progressLoading(
                        groupId = groupId,
                        pipelineName = pipeline.name,
                        extractorName = pipeline.extractor.name,
                        loaderName = loaderName,
                        result = result
                    )
                },
                onStatusChanged = { loaderName, status ->
                    try {
                        metadataRepository.accumulateMetadataByLoader(
                            groupId = groupId,
                            pipelineName = pipeline.name,
                            extractorName = pipeline.extractor.name,
                            loaderName = loaderName,
                            status = status
                        )
                    } catch (e: Throwable) {
                        logger.warn(
                            "ETL pipeline [${pipeline.name}] for group [$groupId] failed to update loading status: ${e.message}",
                            e
                        )
                    }
                }
            )
            return@coroutineScope pipelineResult
        } catch (e: Throwable) {
            logger.error("ETL pipeline [${pipeline.name}] for group [$groupId] failed: ${e.message}", e)
            return@coroutineScope EtlProcessingResult(
                groupId = groupId,
                pipelineName = pipeline.name,
                lastProcessedAt = initTimestamp,
                rowsProcessed = 0,
                status = EtlStatus.FAILED,
                errorMessage = e.message
            )
        }
    }

    suspend fun progressExtracting(groupId: String,
                                   pipelineName: String,
                                   extractorName: String,
                                   result: EtlExtractingResult) {
        try {
            metadataRepository.accumulateMetadataByExtractor(
                groupId = groupId,
                pipelineName = pipelineName,
                extractorName = extractorName,
                errorMessage = result.errorMessage,
                extractDuration = result.duration
            )
        } catch (e: Throwable) {
            logger.warn(
                "ETL pipeline [${pipelineName}] for group [$groupId] failed to update extracting progress: ${e.message}",
                e
            )
        }
    }

    suspend fun progressLoading(
        groupId: String,
        pipelineName: String,
        extractorName: String,
        loaderName: String,
        result: EtlLoadingResult
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
                rowsProcessed = result.processedRows
            )
        } catch (e: Throwable) {
            logger.warn(
                "ETL pipeline [$pipelineName] for group [$groupId] failed to update loading progress: ${e.message}",
                e
            )
        }
    }
}

