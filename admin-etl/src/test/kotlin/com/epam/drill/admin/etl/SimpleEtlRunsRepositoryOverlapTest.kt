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

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies the overlap-aware locking policy of the in-memory [SimpleEtlRunsRepository], which
 * mirrors the production [com.epam.drill.admin.etl.impl.EtlRunsRepositoryImpl] behavior:
 * overlapping bounded periods for the same context serialize, non-overlapping ones run in
 * parallel, and the unbounded (incremental) lane never conflicts with bounded periods.
 */
class SimpleEtlRunsRepositoryOverlapTest {
    private val orchestrator = "test-etl"
    private val context = EtlContext(groupId = "g1")
    private val jan = { d: Int -> LocalDate.of(2024, 1, d) }

    @Test
    fun `overlapping bounded periods for same context cannot both hold the lock`() = runBlocking {
        val repo = SimpleEtlRunsRepository()
        val first = EtlPeriod(jan(1), jan(10))
        val second = EtlPeriod(jan(5), jan(15)) // overlaps first

        assertTrue(repo.tryAcquireLockAndStart(orchestrator, context, "owner-a", 60, first))
        assertFalse(
            repo.tryAcquireLockAndStart(orchestrator, context, "owner-b", 60, second),
            "Overlapping period must be blocked while the first is active"
        )

        // After the first finishes, the overlapping one can proceed.
        repo.markFinishedAndRelease(orchestrator, context, "owner-a", null, first)
        assertTrue(repo.tryAcquireLockAndStart(orchestrator, context, "owner-b", 60, second))
    }

    @Test
    fun `non-overlapping bounded periods for same context run in parallel`() = runBlocking {
        val repo = SimpleEtlRunsRepository()
        val first = EtlPeriod(jan(1), jan(10))
        val second = EtlPeriod(jan(11), jan(20)) // disjoint

        assertTrue(repo.tryAcquireLockAndStart(orchestrator, context, "owner-a", 60, first))
        assertTrue(
            repo.tryAcquireLockAndStart(orchestrator, context, "owner-b", 60, second),
            "Disjoint periods must run concurrently"
        )
    }

    @Test
    fun `unbounded incremental lane never conflicts with a bounded period`() = runBlocking {
        val repo = SimpleEtlRunsRepository()
        val bounded = EtlPeriod(jan(1), jan(10))

        assertTrue(repo.tryAcquireLockAndStart(orchestrator, context, "owner-a", 60, bounded))
        assertTrue(
            repo.tryAcquireLockAndStart(orchestrator, context, "owner-b", 60, EtlPeriod.UNBOUNDED),
            "The incremental lane must be independent of bounded periods"
        )
    }

    @Test
    fun `getUnfinishedTargets returns only bounded non-terminal periods`() = runBlocking {
        val repo = SimpleMetadataRepository()
        val bounded = EtlPeriod(jan(1), jan(10))
        fun meta(pipeline: String, status: EtlStatus, period: EtlPeriod) = EtlMetadata(
            pipelineName = pipeline,
            extractorName = "ex",
            loaderName = "ld",
            lastProcessedAt = java.time.Instant.EPOCH,
            lastRunAt = java.time.Instant.EPOCH,
            status = status,
            period = period,
        )

        repo.saveMetadata(context, meta("p1", EtlStatus.LOADING, bounded))
        repo.saveMetadata(context, meta("p2", EtlStatus.SUCCESS, bounded))
        repo.saveMetadata(context, meta("p3", EtlStatus.FAILED, EtlPeriod.UNBOUNDED)) // incremental lane excluded

        val targets = repo.getUnfinishedTargets(listOf("p1", "p2", "p3"))
        assertTrue(targets.any { it.context == context && it.period == bounded })
        assertFalse(targets.any { it.period == EtlPeriod.UNBOUNDED })
    }
}
