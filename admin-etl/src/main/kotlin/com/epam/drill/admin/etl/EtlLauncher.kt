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
 * Launches [EtlOrchestrator] runs across date ranges (periods).
 */
interface EtlLauncher {

    /**
     * Launches [EtlOrchestrator] runs across date ranges (periods).
     *
     * @param job the [EtlJob] to launch
     * @param snapshotTimestamp the timestamp of the snapshot to process
     * @param skipIfRunning if true, the job will be skipped if it is already running
     */
    suspend fun run(
        job: EtlJob,
        snapshotTimestamp: Instant? = null,
        skipIfRunning: Boolean = true,
    ): EtlJobResult

    /**
     * Schedules [EtlOrchestrator] runs across date ranges (periods) with the specified number of workers.
     *
     * @param context the [EtlContext] for the job
     * @param period the [EtlPeriod] to cover
     * @param workers the number of workers to use
     */
    suspend fun schedule(
        context: EtlContext,
        period: EtlPeriod,
        workers: Int,
    ): List<EtlJob>

    /**
     * Resume the [EtlOrchestrator] runs across date ranges (periods) that were previously scheduled.
     *
     * @param context the [EtlContext] for the job
     * @param period the [EtlPeriod] to cover
     * @param snapshotTimestamp the timestamp of the snapshot to process
     * @param skipIfRunning if true, the job will be skipped if it is already running
     */
    suspend fun resume(
        context: EtlContext,
        period: EtlPeriod,
        snapshotTimestamp: Instant? = null,
        skipIfRunning: Boolean = true,
    ): List<EtlJobResult>

    /**
     * Cancels all active jobs for [context] overlapping [period].
     */
    suspend fun cancel(context: EtlContext, period: EtlPeriod): List<EtlJobResult>

    /**
     * Force-reruns [period] for [context]: cancels any active jobs overlapping it, schedules up
     * to [workers] new jobs covering it, then runs each via
     * [EtlOrchestrator.rerun] (optionally deleting previously loaded data first). Returns the
     * resulting jobs once all of them have finished.
     */
    suspend fun rerun(
        context: EtlContext,
        period: EtlPeriod,
        workers: Int,
        withDataDeletion: Boolean = true,
    ): List<EtlJobResult>

    /** Returns all active ([EtlJobStatus.IDLE]/[EtlJobStatus.RUNNING]) jobs for [context] overlapping [period]. */
    suspend fun getActiveJobs(context: EtlContext? = null, period: EtlPeriod): List<EtlJobResult>

    /** Returns the per-day [EtlDailyStatus] for each day in the bounded [period]. */
    suspend fun getDailyStatuses(context: EtlContext, period: EtlPeriod): List<EtlDailyStatusRow>

    /** Returns the furthest timestamp processed so far for [context], or null if none. */
    suspend fun getLastProcessedTimestamp(context: EtlContext): Instant?

}
