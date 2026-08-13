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
package com.epam.drill.admin.etl

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate

/**
 * In-memory [EtlJobsRepository] used by unit tests, mirroring the overlap/lock semantics of the
 * production [com.epam.drill.admin.etl.impl.EtlJobsRepositoryImpl] (backed by the `etl_jobs`
 * table's GIST EXCLUDE constraint) without requiring a real PostgreSQL `daterange` column.
 */
class SimpleEtlJobsRepository : EtlJobsRepository {

    /** Mutable state tracked for a given [EtlJob] (identified by etlName/context/period). */
    private data class JobState(
        val status: EtlJobStatus = EtlJobStatus.IDLE,
        val workerId: String? = null,
        val lockExpiresAt: Instant? = null,
        val startedAt: Instant? = null,
        val finishedAt: Instant? = null,
        val processedUntilTimestamp: Instant? = null,
        val errorMessage: String? = null,
    )

    private val jobs = mutableMapOf<EtlJob, JobState>()
    private val mutex = Mutex()

    private fun matchesEtlAndContext(job: EtlJob, etlName: String, context: EtlContext?) =
        job.etlName == etlName && job.context == context

    private fun toResult(job: EtlJob, state: JobState): EtlJobResult = EtlJobResult(
        job = job,
        status = state.status,
        processedUntilTimestamp = state.processedUntilTimestamp ?: Instant.EPOCH,
        errorMessage = state.errorMessage,
        workerId = state.workerId,
        lockExpiresAt = state.lockExpiresAt,
    )

    override suspend fun scheduleJob(
        etlName: String,
        context: EtlContext,
        period: EtlPeriod,
    ): EtlJob? = mutex.withLock {
        val overlapping = jobs.any { (job, state) ->
            matchesEtlAndContext(job, etlName, context) &&
                    job.period.overlaps(period) &&
                    state.status in EtlJobStatus.ACTIVE
        }
        if (overlapping) return@withLock null
        val job = EtlJob(etlName = etlName, context = context, period = period)
        jobs[job] = JobState(status = EtlJobStatus.IDLE)
        job
    }

    override suspend fun findResumable(
        etlName: String,
        context: EtlContext,
        period: EtlPeriod,
    ): List<EtlJob> = mutex.withLock {
        val now = Instant.now()
        jobs.filter { (job, state) ->
            matchesEtlAndContext(job, etlName, context) &&
                    job.period.overlaps(period) &&
                    (state.status == EtlJobStatus.IDLE ||
                            (state.status == EtlJobStatus.RUNNING && (state.lockExpiresAt == null || state.lockExpiresAt.isBefore(
                                now
                            ))))
        }.keys.toList()
    }

    override suspend fun cancelJobs(
        etlName: String,
        context: EtlContext,
        period: EtlPeriod,
    ): List<EtlJobResult> = mutex.withLock {
        val toCancel = jobs.filter { (job, state) ->
            matchesEtlAndContext(job, etlName, context) &&
                    job.period.overlaps(period) &&
                    state.status in EtlJobStatus.ACTIVE
        }.keys.toList()
        toCancel.map { job ->
            val updated = jobs.getValue(job).copy(status = EtlJobStatus.CANCELLED, finishedAt = Instant.now())
            jobs[job] = updated
            toResult(job, updated)
        }
    }

    override suspend fun lockJob(
        job: EtlJob,
        workerId: String,
        leaseSeconds: Long,
    ): Boolean = mutex.withLock {
        val current = jobs[job] ?: return@withLock false
        val now = Instant.now()
        val resumable = current.status == EtlJobStatus.IDLE ||
                (current.status == EtlJobStatus.RUNNING && (current.lockExpiresAt == null || current.lockExpiresAt.isBefore(
                    now
                )))
        if (!resumable) return@withLock false
        jobs[job] = current.copy(
            status = EtlJobStatus.RUNNING,
            workerId = workerId,
            lockExpiresAt = now.plusSeconds(leaseSeconds),
            startedAt = now,
            finishedAt = null,
        )
        true
    }

    override suspend fun extendLease(job: EtlJob, workerId: String, leaseSeconds: Long): Boolean = mutex.withLock {
        val current = jobs[job] ?: return@withLock false
        if (current.workerId != workerId || current.status != EtlJobStatus.RUNNING) return@withLock false
        jobs[job] = current.copy(lockExpiresAt = Instant.now().plusSeconds(leaseSeconds))
        true
    }

    override suspend fun markIdle(job: EtlJob, workerId: String, processedUntilTimestamp: Instant): Unit =
        mutex.withLock {
            val current = jobs[job] ?: return@withLock
            if (current.workerId != workerId) return@withLock
            jobs[job] = current.copy(
                status = EtlJobStatus.IDLE,
                processedUntilTimestamp = processedUntilTimestamp,
                finishedAt = Instant.now(),
                workerId = null,
                lockExpiresAt = null,
            )
        }

    override suspend fun markCompleted(job: EtlJob, workerId: String, processedUntilTimestamp: Instant): Unit =
        mutex.withLock {
            val current = jobs[job] ?: return@withLock
            if (current.workerId != workerId) return@withLock
            jobs[job] = current.copy(
                status = EtlJobStatus.COMPLETED,
                processedUntilTimestamp = processedUntilTimestamp,
                finishedAt = Instant.now(),
                workerId = null,
                lockExpiresAt = null,
            )
        }

    override suspend fun markCancelled(job: EtlJob, workerId: String): Unit = mutex.withLock {
        val current = jobs[job] ?: return@withLock
        if (current.workerId != workerId) return@withLock
        jobs[job] = current.copy(
            status = EtlJobStatus.CANCELLED,
            finishedAt = Instant.now(),
            workerId = null,
            lockExpiresAt = null,
        )
    }

    override suspend fun markError(job: EtlJob, workerId: String, errorMessage: String?): Unit = mutex.withLock {
        val current = jobs[job] ?: return@withLock
        if (current.workerId != workerId) return@withLock
        jobs[job] = current.copy(
            status = EtlJobStatus.ERROR,
            errorMessage = errorMessage,
            finishedAt = Instant.now(),
            workerId = null,
            lockExpiresAt = null,
        )
    }

    override suspend fun getActiveJobs(etlName: String, context: EtlContext?, period: EtlPeriod): List<EtlJobResult> = mutex.withLock {
        jobs.filter { (job, state) ->
            matchesEtlAndContext(job, etlName, context) &&
                    job.period.overlaps(period) &&
                    state.status in EtlJobStatus.ACTIVE
        }.map { (job, state) -> toResult(job, state) }
    }

    override suspend fun countRunningJobs(etlName: String?, context: EtlContext?): Long = mutex.withLock {
        jobs.count { (job, state) ->
            (etlName == null || job.etlName == etlName) &&
                    (context == null || job.context == context) &&
                    state.status == EtlJobStatus.RUNNING
        }.toLong()
    }

    override suspend fun getActiveJob(job: EtlJob): EtlJobResult? = mutex.withLock {
        jobs.entries.singleOrNull { (j, state) ->
            j == job && (state.status == EtlJobStatus.RUNNING || state.status == EtlJobStatus.IDLE)
        }?.let { (j, state) -> toResult(j, state) }
    }

    override suspend fun getDailyStatuses(
        etlName: String,
        context: EtlContext,
        period: EtlPeriod,
    ): List<EtlDailyStatusRow> = mutex.withLock {
        val from = requireNotNull(period.from) { "getDailyStatuses requires a bounded period" }
        val to = requireNotNull(period.to) { "getDailyStatuses requires a bounded period" }
        val matching = jobs.entries.filter { (job, _) -> matchesEtlAndContext(job, etlName, context) }
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { day ->
            val covering = matching.filter { (job, _) ->
                val jf = job.period.from ?: LocalDate.MIN
                val jt = job.period.to ?: LocalDate.MAX
                !day.isBefore(jf) && !day.isAfter(jt)
            }
            val status = when {
                covering.isEmpty() -> EtlDailyStatus.UNLOADED
                covering.any { it.value.status == EtlJobStatus.RUNNING } -> EtlDailyStatus.RUNNING
                covering.any { it.value.status == EtlJobStatus.ERROR || it.value.status == EtlJobStatus.CANCELLED } -> EtlDailyStatus.FAILED
                covering.any { it.value.status == EtlJobStatus.COMPLETED } -> EtlDailyStatus.COMPLETED
                covering.any { it.value.status == EtlJobStatus.IDLE } -> EtlDailyStatus.SCHEDULED
                else -> EtlDailyStatus.UNLOADED
            }
            EtlDailyStatusRow(day, status)
        }.toList()
    }

    override suspend fun getLastProcessedTimestamp(
        etlName: String,
        context: EtlContext,
    ): Instant? = mutex.withLock {
        jobs.entries
            .filter { (job, state) ->
                matchesEtlAndContext(
                    job,
                    etlName,
                    context
                ) && state.status != EtlJobStatus.CANCELLED
            }
            .mapNotNull { it.value.processedUntilTimestamp }
            .maxOrNull()
    }
}
