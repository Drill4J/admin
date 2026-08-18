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
import com.epam.drill.admin.etl.EtlJobsRepository
import com.epam.drill.admin.etl.EtlLoadingResult
import com.epam.drill.admin.etl.EtlMetadata
import com.epam.drill.admin.etl.EtlMetadataRepository
import com.epam.drill.admin.etl.EtlOrchestrator
import com.epam.drill.admin.etl.EtlContext
import com.epam.drill.admin.etl.EtlJob
import com.epam.drill.admin.etl.EtlJobResult
import com.epam.drill.admin.etl.EtlJobStatus
import com.epam.drill.admin.etl.EtlPeriod
import com.epam.drill.admin.etl.EtlPipeline
import com.epam.drill.admin.etl.EtlProcessingResult
import com.epam.drill.admin.etl.EtlRow
import com.epam.drill.admin.etl.EtlStatus
import com.epam.drill.admin.etl.flow.ClosableFlow
import com.epam.drill.admin.etl.flow.SubscribableChannelFlow
import io.ktor.util.collections.ConcurrentMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.time.Duration
import java.time.Instant
import java.util.Collections
import kotlin.coroutines.cancellation.CancellationException
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

open class EtlOrchestratorImpl(
    override val name: String,
    override val pipelines: List<EtlPipeline<*, *>>,
    open val metadataRepository: EtlMetadataRepository,
    open val jobsRepository: EtlJobsRepository,
    open val consistencyWindow: Long = 0,
    open val processingDelay: Long = 0,
    open val bufferSize: Int = 2000,
    open val lockLeaseSeconds: Long = 180,
) : EtlOrchestrator {
    private val logger = KotlinLogging.logger {}

    inner class ProgressTracker {
        private val progress: ConcurrentMap<String, Instant> = ConcurrentMap()
        fun register(pipelineName: String, lastProcessedAt: Instant) {
            progress.put(pipelineName, lastProcessedAt)
        }

        fun getProcessedUntilTimestamp(): Instant {
            return progress.values.minOrNull() ?: Instant.EPOCH
        }
    }

    override suspend fun run(job: EtlJob, workerId: String, snapshotTimestamp: Instant?): EtlJobResult {
        return runSafely(job, workerId, snapshotTimestamp)
    }

    override suspend fun rerun(job: EtlJob, workerId: String, withDataDeletion: Boolean): EtlJobResult {
        val context = job.context
        val period = job.period
        logger.info { "ETL job [$workerId] is deleting metadata for rerun $period..." }
        pipelines.map { it.name }.forEach { pipelineName ->
            metadataRepository.deleteMetadataByPipeline(context, pipelineName, period)
        }
        logger.info { "ETL job [$workerId] deleted metadata for rerun $period." }
        if (withDataDeletion) {
            logger.info { "ETL job [$workerId] is deleting data for rerun $period..." }
            pipelines.forEach { it.cleanUp(context, period) }
            logger.info { "ETL job [$workerId] deleted data for rerun $period." }
        }
        return runSafely(job, workerId, snapshotTimestamp = null)
    }

    private suspend fun runSafely(
        job: EtlJob,
        workerId: String,
        snapshotTimestamp: Instant?
    ): EtlJobResult {
        val period = job.period
        val now = Instant.now().minusSeconds(processingDelay)
        val initTimestamp = period.sinceTimestamp ?: Instant.EPOCH
        val finalTimestamp =
            snapshotTimestamp?.takeIf { period.untilTimestamp == null || !it.isAfter(period.untilTimestamp) }
                ?: period.untilTimestamp?.takeIf { it.isBefore(now) }
                ?: now
        check(finalTimestamp.isAfter(initTimestamp)) {
            "ETL job [$workerId] has no new data to process (init=$initTimestamp, final=$finalTimestamp)"
        }
        try {
            return extendLeaseOf(job, workerId) {
                val results = runPipelines(job, workerId, initTimestamp, finalTimestamp)
                val jobFinished = finalTimestamp == period.untilTimestamp
                val minLastProcessedAt = results.minOf { it.lastProcessedAt }
                val hasErrors = results.any { it.status == EtlStatus.FAILED }

                return@extendLeaseOf when {
                    hasErrors -> {
                        val errors = results
                            .filter { it.status == EtlStatus.FAILED }
                            .joinToString(separator = "; ") { it.errorMessage ?: "Unknown error" }
                        job.markError(workerId, errors)
                        EtlJobResult(
                            job = job,
                            status = EtlJobStatus.ERROR,
                            errorMessage = errors,
                            processedUntilTimestamp = minLastProcessedAt
                        )
                    }

                    !hasErrors && jobFinished -> {
                        job.markCompleted(workerId, finalTimestamp)
                        EtlJobResult(
                            job = job,
                            status = EtlJobStatus.COMPLETED,
                            processedUntilTimestamp = finalTimestamp
                        )
                    }

                    else -> {
                        job.markIdle(workerId, finalTimestamp)
                        EtlJobResult(
                            job = job,
                            status = EtlJobStatus.IDLE,
                            processedUntilTimestamp = finalTimestamp
                        )
                    }
                }
            }
        } catch (e: CancellationException) {
            logger.info { "ETL job [$workerId] interrupted: ${e.message}" }
            job.markCancelled(workerId)
            return EtlJobResult(
                job = job,
                status = EtlJobStatus.CANCELLED,
                processedUntilTimestamp = initTimestamp
            )
        } catch (e: Throwable) {
            logger.error(e) { "ETL job [$workerId] failed: ${e.message}" }
            job.markError(workerId, e.message ?: e::class.java.name)
            return EtlJobResult(
                job = job,
                status = EtlJobStatus.ERROR,
                errorMessage = e.message,
                processedUntilTimestamp = initTimestamp
            )
        }
    }

    private suspend fun extendLeaseOf(job: EtlJob, workerId: String, body: suspend () -> EtlJobResult): EtlJobResult {
        return trackProgressOf {
            body()
        }.every(lockLeaseSeconds.seconds / 2) {
            runCatching {
                val extended = jobsRepository.extendLease(job, workerId, lockLeaseSeconds)
                if (!extended) {
                    this@every.cancel(CancellationException("Job was cancelled"))
                }
            }.onFailure { e ->
                logger.warn(e) { "ETL job [$workerId] failed to extend run-lock lease" }
                this@every.cancel(CancellationException(e.message))
            }
        }
    }

    private suspend fun EtlJob.markCompleted(workerId: String, processedUntilTimestamp: Instant) {
        runCatching { jobsRepository.markCompleted(this, workerId, processedUntilTimestamp) }.onFailure {
            logger.warn(it) { "ETL job [$workerId] failed to mark job as COMPLETED" }
        }
    }

    private suspend fun EtlJob.markIdle(workerId: String, processedUntilTimestamp: Instant) {
        runCatching { jobsRepository.markIdle(this, workerId, processedUntilTimestamp) }.onFailure {
            logger.warn(it) { "ETL job [$workerId] failed to mark job as IDLE" }
        }
    }

    private suspend fun EtlJob.markCancelled(workerId: String) {
        runCatching { jobsRepository.markCancelled(this, workerId) }.onFailure {
            logger.warn(it) { "ETL job [$workerId] failed to mark job as CANCELLED" }
        }
    }

    private suspend fun EtlJob.markError(workerId: String, errorMessage: String) {
        runCatching { jobsRepository.markError(this, workerId, errorMessage) }.onFailure {
            logger.warn(it) { "ETL job [$workerId] failed to mark job as ERROR" }
        }
    }

    private suspend fun runPipelines(
        job: EtlJob,
        workerId: String,
        initTimestamp: Instant,
        finalTimestamp: Instant,
    ): List<EtlProcessingResult> = withContext(Dispatchers.IO) {
        logger.info("ETL job [$workerId] is starting ${job.period}...")
        val results = Collections.synchronizedList(mutableListOf<EtlProcessingResult>())
        val progressTracker = ProgressTracker()
        val duration = measureTimeMillis {
            trackProgressOf {
                // Group pipelines by extractor name; pipelines in the same group share one extractor run
                val extractorGroups = pipelines.groupBy { it.extractor.name }
                extractorGroups.map { (_, groupedPipelines) ->
                    async {
                        @Suppress("UNCHECKED_CAST")
                        val typedPipelines = groupedPipelines as List<EtlPipeline<EtlRow, *>>
                        results += runPipelineGroupByExtractor(
                            context = job.context,
                            period = job.period,
                            groupedPipelines = typedPipelines,
                            extractor = typedPipelines.first().extractor,
                            initTimestamp = initTimestamp,
                            finalTimestamp = finalTimestamp,
                            progressTracker = progressTracker
                        )
                    }
                }.awaitAll()
            }.every(1.minutes) {
                val processedUntilTimestamp = progressTracker.getProcessedUntilTimestamp()
                val total = Duration.between(initTimestamp, finalTimestamp).toMillis()
                val processed = Duration.between(initTimestamp, processedUntilTimestamp).toMillis()
                val progress = ((processed.toDouble() / total) * 100).toInt()

                logger.info { "ETL job [$workerId] is still running ${job.period} ... Progress: $progress%" }
                jobsRepository.updateProcessedUntilTimestamp(
                    job,
                    workerId,
                    processedUntilTimestamp
                )
            }
        }
        logger.info {
            val rowsProcessed = results.sumOf { it.rowsProcessed }
            val failures = results.count { it.status == EtlStatus.FAILED }
            if (rowsProcessed == 0L && failures == 0)
                "ETL job [$workerId] completed ${job.period} in ${duration}ms, no new rows"
            else
                "ETL job [$workerId] completed ${job.period} in ${duration}ms, rows processed: $rowsProcessed, failures: $failures"
        }
        return@withContext results
    }

    /**
     * Runs a group of pipelines that share the same extractor.
     * The extractor is executed exactly once; its output is broadcast to all pipelines in the group.
     */
    private suspend fun <T : EtlRow> runPipelineGroupByExtractor(
        context: EtlContext,
        period: EtlPeriod,
        groupedPipelines: List<EtlPipeline<T, *>>,
        extractor: DataExtractor<T> = groupedPipelines.first().extractor,
        initTimestamp: Instant,
        finalTimestamp: Instant,
        progressTracker: ProgressTracker,
    ): List<EtlProcessingResult> = coroutineScope {

        // Compute per-pipeline sinceTimestamp from metadata
        val sinceTimestamps: Map<String, Instant> = groupedPipelines.associate { pipeline ->
            val metadata = metadataRepository.getMetadata(context, pipeline.name, period)
            val sinceTimestamp = if (metadata?.lastProcessedAt != null)
                metadata.lastProcessedAt.minusSeconds(consistencyWindow)
            else
                initTimestamp
            progressTracker.register(pipeline.name, sinceTimestamp)
            pipeline.name to sinceTimestamp
        }

        val (skippedPipelines, activePipelines) = groupedPipelines.partition { pipeline ->
            (sinceTimestamps[pipeline.name] ?: initTimestamp) >= finalTimestamp
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
                logger.debug { "ETL pipeline [${pipeline.name}] for $period is already up-to-date." }
            }
        }

        if (activePipelines.isEmpty()) return@coroutineScope skippedResults

        for (pipeline in activePipelines) {
            initPipelineMetadata(
                pipeline = pipeline,
                lastProcessedAt = sinceTimestamps[pipeline.name] ?: initTimestamp,
                context = context,
                period = period
            )
        }

        val minLastProcessedAt = activePipelines.mapNotNull { sinceTimestamps[it.name] }.min()
        val sharedFlow = SubscribableChannelFlow<T>(bufferSize)
        val jobs = activePipelines.map { pipeline ->
            async {
                runPipelineWithExtractionFlow(
                    context = context,
                    period = period,
                    pipeline = pipeline,
                    sinceTimestamp = sinceTimestamps[pipeline.name] ?: initTimestamp,
                    untilTimestamp = finalTimestamp,
                    sharedFlow = sharedFlow.subscribe(),
                    progressTracker = progressTracker,
                )
            }
        }
        sharedFlow.waitForSubscribers(jobs.count { it.isActive })
        extractRowsToExtractionFlow(
            context = context,
            period = period,
            extractor = extractor,
            sinceTimestamp = minLastProcessedAt,
            untilTimestamp = finalTimestamp,
            sharedFlow = sharedFlow,
            activePipelines = activePipelines,
        )

        skippedResults + jobs.awaitAll()
    }

    private suspend fun <T : EtlRow> extractRowsToExtractionFlow(
        context: EtlContext,
        period: EtlPeriod,
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
                        progressExtracting(context, period, pipeline.name, extractor.name, result)
                    }
                }
            )
        } catch (e: Throwable) {
            logger.debug(e) { "ETL extractor [${extractor.name}] for $period failed: ${e.message}" }
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
        period: EtlPeriod,
        pipeline: EtlPipeline<T, R>,
        sinceTimestamp: Instant,
        untilTimestamp: Instant,
        sharedFlow: ClosableFlow<T>,
        progressTracker: ProgressTracker,
    ): EtlProcessingResult {
        return try {
            pipeline.execute(
                context = context,
                sinceTimestamp = sinceTimestamp,
                untilTimestamp = untilTimestamp,
                extractionFlow = sharedFlow,
                onLoadingProgress = { result ->
                    saveLoadingProgress(context, period, pipeline.name, result)
                    progressTracker.register(pipeline.name, result.lastProcessedAt)
                },
                onStatusChanged = { status ->
                    if (status == EtlStatus.SUCCESS) {
                        saveStatusChange(context, period, pipeline.name, status, untilTimestamp)
                        progressTracker.register(pipeline.name, untilTimestamp)
                    } else {
                        saveStatusChange(context, period, pipeline.name, status, null)
                    }
                }
            )
        } catch (e: Throwable) {
            logger.error("ETL pipeline [${pipeline.name}] for $period failed: ${e.message}", e)
            EtlProcessingResult(
                context = context,
                pipelineName = pipeline.name,
                lastProcessedAt = sinceTimestamp,
                rowsProcessed = 0,
                status = EtlStatus.FAILED,
                errorMessage = e.message,
            )
        } finally {
            sharedFlow.close()
        }
    }

    private suspend fun progressExtracting(
        context: EtlContext,
        period: EtlPeriod,
        pipelineName: String,
        extractorName: String,
        result: EtlExtractingResult,
    ) {
        try {
            metadataRepository.accumulateMetadataByExtractor(
                context = context,
                pipelineName = pipelineName,
                period = period,
                errorMessage = result.errorMessage,
                extractDuration = result.duration,
            )
        } catch (e: Throwable) {
            logger.warn(
                "ETL pipeline [$pipelineName] for $period failed to update extracting progress: ${e.message}",
                e
            )
        }
    }

    private suspend fun saveLoadingProgress(
        context: EtlContext,
        period: EtlPeriod,
        pipelineName: String,
        result: EtlLoadingResult,
    ) {
        try {
            metadataRepository.accumulateMetadataByLoader(
                context = context,
                pipelineName = pipelineName,
                period = period,
                errorMessage = result.errorMessage,
                lastProcessedAt = result.lastProcessedAt,
                loadDuration = result.duration ?: 0L,
                rowsProcessed = result.processedRows,
            )
        } catch (e: Throwable) {
            logger.warn(
                "ETL pipeline [$pipelineName] for $period failed to update loading progress: ${e.message}",
                e
            )
        }
    }

    private suspend fun saveStatusChange(
        context: EtlContext,
        period: EtlPeriod,
        pipelineName: String,
        status: EtlStatus,
        lastProcessedAt: Instant?,
    ) {
        try {
            metadataRepository.accumulateMetadataByLoader(
                context = context,
                pipelineName = pipelineName,
                period = period,
                status = status,
                lastProcessedAt = lastProcessedAt,
            )
        } catch (e: Throwable) {
            logger.warn(
                "ETL pipeline [${pipelineName}] for $period failed to update loading status: ${e.message}",
                e
            )
        }
    }

    suspend fun initPipelineMetadata(
        pipeline: EtlPipeline<*, *>,
        lastProcessedAt: Instant,
        context: EtlContext,
        period: EtlPeriod
    ) {
        try {
            metadataRepository.saveMetadata(
                context,
                EtlMetadata(
                    pipelineName = pipeline.name,
                    extractorName = pipeline.extractor.name,
                    loaderName = pipeline.loader.name,
                    status = EtlStatus.EXTRACTING,
                    lastProcessedAt = lastProcessedAt,
                    period = period,
                )
            )
        } catch (e: Throwable) {
            logger.warn(
                "ETL pipeline [${pipeline.name}] for $period failed to save initial metadata: ${e.message}",
                e
            )
        }
    }
}
