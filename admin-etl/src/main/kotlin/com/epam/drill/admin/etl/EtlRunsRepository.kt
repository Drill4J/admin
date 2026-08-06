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

/**
 * Repository for managing ETL run state and distributed locking for orchestrators.
 */
interface EtlRunsRepository {

    /**
     * Atomic UPSERT that simultaneously claims the lock and starts the run for a given [period].
     *
     * Bounded periods ([EtlPeriod.isBounded]) get their own lock row (keyed by period), so
     * non-overlapping periods for the same context run in parallel. Acquisition of a bounded
     * period fails when another *active* (non-expired) bounded lock for the same orchestrator and
     * context holds an **overlapping** day range — such periods are serialized, not merged. The
     * unbounded/incremental lane is a separate lane and never conflicts with bounded periods.
     *
     * @return true if the lock was acquired, false if a conflicting active lock exists.
     */
    suspend fun tryAcquireLockAndStart(
        orchestratorName: String,
        context: EtlContext,
        ownerId: String,
        leaseSeconds: Long,
        period: EtlPeriod = EtlPeriod.UNBOUNDED,
    ): Boolean

    /**
     * Extends the lease on a row currently owned by [ownerId]. Used by the orchestrator's
     * progress tracker to keep the lock alive while a run is in progress.
     */
    suspend fun extendLease(
        orchestratorName: String,
        context: EtlContext,
        ownerId: String,
        leaseSeconds: Long,
        period: EtlPeriod = EtlPeriod.UNBOUNDED,
    )

    /**
     * Returns the last successfully processed timestamp for the given orchestrator, context and
     * [period], or null if no completed run exists yet.
     */
    suspend fun getLastProcessedAt(
        orchestratorName: String,
        context: EtlContext,
        period: EtlPeriod = EtlPeriod.UNBOUNDED,
    ): java.time.Instant?

    /**
     * Marks the run as finished and releases the lock.
     * @param lastProcessedAt The minimum lastProcessedAt from all successfully completed pipelines, or null if none succeeded.
     */
    suspend fun markFinishedAndRelease(
        orchestratorName: String,
        context: EtlContext,
        ownerId: String,
        lastProcessedAt: java.time.Instant? = null,
        period: EtlPeriod = EtlPeriod.UNBOUNDED,
    )
}
