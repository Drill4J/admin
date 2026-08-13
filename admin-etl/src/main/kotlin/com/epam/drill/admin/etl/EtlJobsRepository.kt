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

import java.time.Instant

/**
 * Repository for `etl_jobs`.
 */
interface EtlJobsRepository {


    /**
     * Inserts a new [EtlJobStatus.IDLE] job for [etlName]/[context]/[period].
     * Returns null if the insert failed (e.g. an overlapping active job already exists and the
     * unique/exclude constraint rejected it).
     */
    suspend fun scheduleJob(
        etlName: String,
        context: EtlContext,
        period: EtlPeriod,
    ): EtlJob?

    /**
     * Returns jobs for [etlName]/[context] whose day range overlaps [period] and that are
     * eligible to be (re)started: [EtlJobStatus.IDLE], or [EtlJobStatus.RUNNING] with an expired
     * lease.
     */
    suspend fun findResumable(
        etlName: String,
        context: EtlContext,
        period: EtlPeriod,
    ): List<EtlJob>

    /** Cancels (sets [EtlJobStatus.CANCELLED]) active jobs matching [etlName]/[context]/[period]. */
    suspend fun cancelJobs(
        etlName: String,
        context: EtlContext,
        period: EtlPeriod,
    ): List<EtlJobResult>

    /**
     * Atomically locks [job] for running: sets it to [EtlJobStatus.RUNNING], stamps [workerId]
     * and a lease of [leaseSeconds]. Only succeeds when the job is currently [EtlJobStatus.IDLE]
     * or [EtlJobStatus.RUNNING] with an expired lease. Returns the locked [EtlJob], or null if
     * the lock could not be acquired (already running/locked by someone else, or job missing).
     */
    suspend fun lockJob(
        job: EtlJob,
        workerId: String,
        leaseSeconds: Long,
    ): Boolean

    /** Extends the lease of the job currently locked by [workerId]. */
    suspend fun extendLease(
        job: EtlJob,
        workerId: String,
        leaseSeconds: Long,
    ): Boolean

    /**
     * Marks the job owned by [workerId] as [EtlJobStatus.IDLE] (snapshot reached, or ran out of
     * data, but the period hasn't ended) and releases the lock.
     */
    suspend fun markIdle(
        job: EtlJob,
        workerId: String,
        processedUntilTimestamp: Instant,
    )

    /** Marks the job owned by [workerId] as [EtlJobStatus.COMPLETED] and releases the lock. */
    suspend fun markCompleted(
        job: EtlJob,
        workerId: String,
        processedUntilTimestamp: Instant,
    )

    /**
     * Marks the job owned by [workerId] as [EtlJobStatus.CANCELLED] and releases the lock.
     */
    suspend fun markCancelled(
        job: EtlJob,
        workerId: String,
    )

    /** Marks the job owned by [workerId] as [EtlJobStatus.ERROR] and releases the lock. */
    suspend fun markError(
        job: EtlJob,
        workerId: String,
        errorMessage: String?,
    )

    /** Returns active ([EtlJobStatus.IDLE]/[EtlJobStatus.RUNNING]) jobs matching [etlName]/[context]/[period]. */
    suspend fun getActiveJobs(
        etlName: String,
        context: EtlContext?,
        period: EtlPeriod,
    ): List<EtlJobResult>

    /** Returns the number of jobs currently in [EtlJobStatus.RUNNING] for [etlName]. */
    suspend fun countRunningJobs(
        etlName: String?,
        context: EtlContext?
    ): Long

    /** Returns the single job, or null if no such job exists or job is not active. */
    suspend fun getActiveJob(job: EtlJob): EtlJobResult?

    /**
     * Returns per-day statuses for [period] derived from jobs matching [etlName]/[context].
     * Days with no covering job are reported as [EtlDailyStatus.UNLOADED].
     */
    suspend fun getDailyStatuses(
        etlName: String,
        context: EtlContext,
        period: EtlPeriod,
    ): List<EtlDailyStatusRow>

    /**
     * Returns the furthest `processed_until_timestamp` reached by any (non-canceled) job for
     * [etlName]/[context], or null if no job has recorded progress yet.
     */
    suspend fun getLastProcessedTimestamp(
        etlName: String,
        context: EtlContext,
    ): Instant?
}
