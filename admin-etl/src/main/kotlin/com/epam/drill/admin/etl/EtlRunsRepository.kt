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
     * Atomic UPSERT that simultaneously claims the lock and starts the run.
     * @return true if the lock was acquired, false if another instance currently holds an active lease.
     */
    suspend fun tryAcquireLockAndStart(
        orchestratorName: String,
        context: EtlContext,
        ownerId: String,
        leaseSeconds: Long,
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
    )

    /**
     * Returns the last successfully processed timestamp for the given orchestrator and context,
     * or null if no completed run exists yet.
     */
    suspend fun getLastProcessedAt(
        orchestratorName: String,
        context: EtlContext,
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
    )
}
