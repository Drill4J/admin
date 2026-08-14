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

import com.epam.drill.admin.etl.EtlContext
import com.epam.drill.admin.etl.EtlDailyStatusRow
import com.epam.drill.admin.etl.EtlJob
import com.epam.drill.admin.etl.EtlJobResult
import com.epam.drill.admin.etl.EtlJobStatus
import com.epam.drill.admin.etl.EtlJobsRepository
import com.epam.drill.admin.etl.EtlLauncher
import com.epam.drill.admin.etl.EtlOrchestrator
import com.epam.drill.admin.etl.EtlPeriod
import com.epam.drill.admin.etl.EtlWorkerPool
import com.epam.drill.admin.etl.exception.LockAcquisitionException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import mu.KotlinLogging
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class EtlLauncherImpl(
    private val orchestrator: EtlOrchestrator,
    private val jobsRepository: EtlJobsRepository,
    private val lockLeaseSeconds: Long = 180,
    private val lockRetryDelay: Long = 10000L,
    private val lockAttempts: Int = 60,
    private val workerPool: EtlWorkerPool = SemaphoreWorkerPool(maxWorkers = 4),
) : EtlLauncher {
    private val logger = KotlinLogging.logger {}
    private val etlName get() = orchestrator.name

    override suspend fun run(
        job: EtlJob,
        snapshotTimestamp: Instant?,
        skipIfRunning: Boolean
    ): EtlJobResult {
        return executeLocked(job, skipIfRunning) { workerId ->
            orchestrator.run(job, workerId, snapshotTimestamp)
        }
    }


    override suspend fun schedule(context: EtlContext, period: EtlPeriod, workers: Int): List<EtlJob> {
        val chunks = partition(period, workers)
        return chunks.mapNotNull { jobsRepository.scheduleJob(etlName, context, it) }
    }

    override suspend fun resume(
        context: EtlContext,
        period: EtlPeriod,
        snapshotTimestamp: Instant?,
        skipIfRunning: Boolean
    ): List<EtlJobResult> = coroutineScope {
        val resumable = jobsRepository.findResumable(etlName, context, period)
        resumable.map { job ->
            async {
                executeLocked(job, skipIfRunning) { workerId ->
                    orchestrator.run(job, workerId, snapshotTimestamp)
                }
            }
        }.awaitAll()
    }

    override suspend fun cancel(context: EtlContext, period: EtlPeriod): List<EtlJobResult> =
        jobsRepository.cancelJobs(etlName, context, period)

    override suspend fun rerun(
        context: EtlContext,
        period: EtlPeriod,
        workers: Int,
        withDataDeletion: Boolean,
    ): List<EtlJobResult> = coroutineScope {
        cancel(context, period)
        schedule(context, period, workers).map { job ->
            async {
                executeLocked(job, skipIfRunning = false) { workerId ->
                    orchestrator.rerun(job, workerId, withDataDeletion)
                }
            }
        }.awaitAll()
    }

    override suspend fun getActiveJobs(context: EtlContext?, period: EtlPeriod): List<EtlJobResult> =
        jobsRepository.getActiveJobs(etlName, context, period)

    override suspend fun getDailyStatuses(context: EtlContext, period: EtlPeriod): List<EtlDailyStatusRow> =
        jobsRepository.getDailyStatuses(etlName, context, period)

    override suspend fun getLastProcessedTimestamp(context: EtlContext): Instant? =
        jobsRepository.getLastProcessedTimestamp(etlName, context)


    /**
     * The simplest partitioning strategy: splits a bounded [period] into up to [workers]
     * roughly equal day-range chunks. Returns [period] unchanged when it's unbounded or
     * [workers] `<= 1`.
     */
    private fun partition(period: EtlPeriod, workers: Int): List<EtlPeriod> {
        val from = period.from
        val to = period.to
        if (from == null || to == null || workers <= 1) return listOf(period)

        val totalDays = ChronoUnit.DAYS.between(from, to) + 1
        val chunkCount = minOf(workers.toLong(), totalDays).coerceAtLeast(1).toInt()
        val baseSize = totalDays / chunkCount
        val remainder = totalDays % chunkCount

        val periods = mutableListOf<EtlPeriod>()
        var cursor: LocalDate = from
        for (i in 0 until chunkCount) {
            val size = baseSize + if (i < remainder) 1 else 0
            if (size <= 0) continue
            val end = cursor.plusDays(size - 1)
            periods += EtlPeriod(cursor, end)
            cursor = end.plusDays(1)
        }
        return periods
    }

    /**
     * Locks [job] via [EtlJobsRepository.lockJob], runs [block] while periodically extending the
     * lease, then marks the job COMPLETED/IDLE/ERROR based on the outcome. Returns SKIPPED
     * results (one per pipeline) without invoking [block] if the lock could not be acquired.
     */
    private suspend fun executeLocked(
        job: EtlJob,
        skipIfRunning: Boolean,
        block: suspend (workerId: String) -> EtlJobResult,
    ): EtlJobResult = workerPool.withWorker(job) { workerId ->
        val skippedJob = tryToLock(job, workerId, skipIfRunning)
        if (skippedJob != null) {
            return@withWorker skippedJob
        }
        block(workerId)
    }

    /**
     * Attempts to lock [job], retrying every [lockRetryDelay] for up to 60 attempts.
     * Throw an exception if the lock cannot be acquired after 60 attempts.
     * Returns null if the lock was acquired, or a skipped job if [skipIfRunning] is true and the job is already locked.
     */
    private suspend fun tryToLock(job: EtlJob, workerId: String, skipIfRunning: Boolean): EtlJobResult? {
        var attempts = 0
        var locked: Boolean
        var activeJob: EtlJobResult?
        do {
            locked = jobsRepository.lockJob(job, workerId, lockLeaseSeconds)
            if (!locked) {
                activeJob = jobsRepository.getActiveJob(job)
                if (activeJob == null) {
                    throw LockAcquisitionException("ETL [${job.etlName}] for ${job.period} cannot run because it was not scheduled")
                }
                if (skipIfRunning && activeJob.status == EtlJobStatus.RUNNING) {
                    logger.info {
                        "ETL [$etlName] for ${job.period} is already running by [${activeJob?.workerId}], skipping..."
                    }
                    return activeJob
                }
                attempts++
                if (attempts % 10 == 0) {
                    //log every 10 attempts
                    logger.info {
                        "ETL [$etlName] for ${job.period} is still running, waiting for lock..."
                    }
                }
                delay(lockRetryDelay)
            }
        } while (!locked && attempts < lockAttempts)
        if (!locked) {
            throw LockAcquisitionException("ETL [$etlName] for ${job.period} is still running after $attempts attempts")
        }
        return null
    }
}
