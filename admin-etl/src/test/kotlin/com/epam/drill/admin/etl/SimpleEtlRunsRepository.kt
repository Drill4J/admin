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

/**
 * In-memory [EtlRunsRepository] used by unit tests. Uses a simple mutex to guard the
 * shared map so concurrent claim/release calls behave deterministically without a DB.
 */
class SimpleEtlRunsRepository : EtlRunsRepository {
    data class RunState(
        var status: EtlRunStatus = EtlRunStatus.IDLE,
        var runsCount: Long = 0,
        var lastStartedAt: Instant? = null,
        var lastFinishedAt: Instant? = null,
        var lockOwner: String? = null,
        var lockExpiresAt: Instant? = null,
        var lastProcessedAt: Instant? = null,
    )

    private data class Key(val orchestratorName: String, val context: EtlContext, val period: EtlPeriod)

    private val store = mutableMapOf<Key, RunState>()
    private val mutex = Mutex()

    fun snapshot(
        orchestratorName: String,
        context: EtlContext,
        period: EtlPeriod = EtlPeriod.UNBOUNDED,
    ): RunState? = store[Key(orchestratorName, context, period)]?.copy()

    override suspend fun tryAcquireLockAndStart(
        orchestratorName: String,
        context: EtlContext,
        ownerId: String,
        leaseSeconds: Long,
        period: EtlPeriod,
    ): Boolean = mutex.withLock {
        val now = Instant.now()

        if (period.isBounded) {
            val overlappingActive = store.entries.any { (key, state) ->
                key.orchestratorName == orchestratorName &&
                        key.context == context &&
                        key.period != period &&
                        key.period.isBounded &&
                        key.period.overlaps(period) &&
                        state.lockOwner != null &&
                        state.lockOwner != ownerId &&
                        state.status == EtlRunStatus.RUNNING &&
                        state.lockExpiresAt?.isAfter(now) == true
            }
            if (overlappingActive) return@withLock false
        }

        val state = store.getOrPut(Key(orchestratorName, context, period)) { RunState() }
        val held = state.lockOwner != null &&
                state.lockExpiresAt?.isAfter(now) == true &&
                state.lockOwner != ownerId
        if (held) return@withLock false
        state.lockOwner = ownerId
        state.lockExpiresAt = now.plusSeconds(leaseSeconds)
        state.status = EtlRunStatus.RUNNING
        state.runsCount += 1
        state.lastStartedAt = now
        true
    }

    override suspend fun extendLease(
        orchestratorName: String,
        context: EtlContext,
        ownerId: String,
        leaseSeconds: Long,
        period: EtlPeriod,
    ) = mutex.withLock {
        val state = store[Key(orchestratorName, context, period)] ?: return@withLock
        if (state.lockOwner == ownerId) {
            state.lockExpiresAt = Instant.now().plusSeconds(leaseSeconds)
        }
    }

    override suspend fun getLastProcessedAt(
        orchestratorName: String,
        context: EtlContext,
        period: EtlPeriod,
    ): Instant? = mutex.withLock {
        store[Key(orchestratorName, context, period)]?.lastProcessedAt
    }

    override suspend fun markFinishedAndRelease(
        orchestratorName: String,
        context: EtlContext,
        ownerId: String,
        lastProcessedAt: Instant?,
        period: EtlPeriod,
    ) = mutex.withLock {
        val state = store[Key(orchestratorName, context, period)] ?: return@withLock
        if (state.lockOwner == ownerId) {
            state.status = EtlRunStatus.IDLE
            state.lastFinishedAt = Instant.now()
            state.lockOwner = null
            state.lockExpiresAt = null
            state.lastProcessedAt = lastProcessedAt
        }
    }
}
