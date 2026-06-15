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
    )

    private val store = mutableMapOf<Pair<String, EtlContext>, RunState>()
    private val mutex = Mutex()

    fun snapshot(orchestratorName: String, context: EtlContext): RunState? = store[orchestratorName to context]?.copy()

    override suspend fun tryAcquireLockAndStart(
        orchestratorName: String,
        context: EtlContext,
        ownerId: String,
        leaseSeconds: Long,
    ): Boolean = mutex.withLock {
        val key = orchestratorName to context
        val now = Instant.now()
        val state = store.getOrPut(key) { RunState() }
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
    ) = mutex.withLock {
        val state = store[orchestratorName to context] ?: return@withLock
        if (state.lockOwner == ownerId) {
            state.lockExpiresAt = Instant.now().plusSeconds(leaseSeconds)
        }
    }

    override suspend fun markFinishedAndRelease(
        orchestratorName: String,
        context: EtlContext,
        ownerId: String,
    ) = mutex.withLock {
        val state = store[orchestratorName to context] ?: return@withLock
        if (state.lockOwner == ownerId) {
            state.status = EtlRunStatus.IDLE
            state.lastFinishedAt = Instant.now()
            state.lockOwner = null
            state.lockExpiresAt = null
        }
    }
}
