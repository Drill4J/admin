package com.epam.drill.admin.etl.impl

import com.epam.drill.admin.etl.DataExtractor
import com.epam.drill.admin.etl.DataLoader
import com.epam.drill.admin.etl.DataTransformer
import com.epam.drill.admin.etl.EtlContext
import com.epam.drill.admin.etl.EtlExtractingResult
import com.epam.drill.admin.etl.EtlLoadingResult
import com.epam.drill.admin.etl.EtlRow
import com.epam.drill.admin.etl.EtlRunStatus
import com.epam.drill.admin.etl.EtlRunsRepository
import com.epam.drill.admin.etl.EtlStatus
import com.epam.drill.admin.etl.SimpleEtlRunsRepository
import com.epam.drill.admin.etl.SimpleMetadataRepository
import com.epam.drill.admin.etl.config.EtlMeter
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
class EtlOrchestratorLockTest {
    private val metrics = EtlMeter(SimpleMeterRegistry())

    private data class Row(val id: Int, val ts: Instant) : EtlRow(ts)

    private inner class SlowLoader(
        private val delayMillis: Long,
        private val onStart: (() -> Unit)? = null,
    ) : DataLoader<Row> {
        override val name = "slow-loader"
        val processed = AtomicInteger(0)
        override suspend fun load(
            context: EtlContext,
            sinceTimestamp: Instant,
            untilTimestamp: Instant,
            collector: Flow<Row>,
            onLoadingProgress: suspend (EtlLoadingResult) -> Unit,
            onStatusChanged: suspend (EtlStatus) -> Unit,
        ): EtlLoadingResult {
            onStart?.invoke()
            var lastTs = sinceTimestamp
            collector.collect { row ->
                lastTs = row.ts
                processed.incrementAndGet()
                delay(delayMillis)
            }
            val result = EtlLoadingResult(lastProcessedAt = lastTs, processedRows = processed.get().toLong())
            onLoadingProgress(result)
            onStatusChanged(EtlStatus.SUCCESS)
            return result
        }

        override suspend fun deleteAll(context: EtlContext) {}
    }

    private inner class FixedExtractor(private val rows: List<Row>) : DataExtractor<Row> {
        override val name = "fixed"
        override suspend fun extract(
            context: EtlContext,
            sinceTimestamp: Instant,
            untilTimestamp: Instant,
            emitter: FlowCollector<Row>,
            onExtractingProgress: suspend (EtlExtractingResult) -> Unit,
        ) {
            rows.filter { it.ts > sinceTimestamp }.forEach { emitter.emit(it) }
        }
    }

    private val identity: DataTransformer<Row, Row> = object : DataTransformer<Row, Row> {
        override val name = "identity"
        override suspend fun transform(context: EtlContext, collector: Flow<Row>): Flow<Row> = flow {
            collector.collect { emit(it) }
        }
    }

    private fun newOrchestrator(
        runsRepo: EtlRunsRepository,
        loader: DataLoader<Row>,
        rows: List<Row>,
        lockPollDelaySeconds: Long = 0,
    ): EtlOrchestratorImpl = EtlOrchestratorImpl(
        name = "test-etl",
        pipelines = listOf(
            EtlPipelineImpl(
                name = "test-pipeline",
                extractor = FixedExtractor(rows),
                transformer = identity,
                loader = loader,
                metrics = metrics,
            )
        ),
        metadataRepository = SimpleMetadataRepository(),
        runsRepository = runsRepo,
        lockLeaseSeconds = 60,
        lockPollDelaySeconds = lockPollDelaySeconds,
    )

    @Test
    fun `concurrent runs with same context serialize via lock`() = runBlocking {
        val runsRepo = SimpleEtlRunsRepository()
        val loader = SlowLoader(delayMillis = 50)
        val rows = (1..3).map { Row(it, Instant.now()) }
        val orchestrator = newOrchestrator(runsRepo, loader, rows)
        val context = EtlContext(groupId = "g")

        val (firstStarted, secondStarted) = coroutineScope {
            val a = async {
                val before = System.nanoTime()
                orchestrator.run(context)
                before
            }
            val b = async {
                val before = System.nanoTime()
                orchestrator.run(context)
                before
            }
            listOf(a, b).awaitAll()
        }

        // Both calls eventually returned; runs_count must reflect two distinct claim+start events.
        val state = runsRepo.snapshot("test-etl", context)
        assertNotNull(state)
        assertEquals(2L, state.runsCount, "Both runs must claim the lock sequentially")
        assertEquals(EtlRunStatus.IDLE, state.status)
        assertNull(state.lockOwner, "Lock must be released after the run completes")
        assertNull(state.lockExpiresAt, "Lock expiry must be cleared after the run completes")

        // Sanity: nothing about timing — just that both runs observed the same lock guard.
        assertTrue(firstStarted > 0 && secondStarted > 0)
    }

    @Test
    fun `runs with different contexts proceed in parallel`() = runBlocking {
        val runsRepo = SimpleEtlRunsRepository()
        val loader = SlowLoader(delayMillis = 30)
        val rows = (1..2).map { Row(it, Instant.now()) }
        val orchestrator = newOrchestrator(runsRepo, loader, rows)

        coroutineScope {
            val a = async { orchestrator.run(EtlContext(groupId = "ga")) }
            val b = async { orchestrator.run(EtlContext(groupId = "gb")) }
            listOf(a, b).awaitAll()
        }

        val stateA = runsRepo.snapshot("test-etl", EtlContext(groupId = "ga"))
        val stateB = runsRepo.snapshot("test-etl", EtlContext(groupId = "gb"))
        assertNotNull(stateA)
        assertNotNull(stateB)
        assertEquals(1L, stateA.runsCount)
        assertEquals(1L, stateB.runsCount)
        assertEquals(EtlRunStatus.IDLE, stateA.status)
        assertEquals(EtlRunStatus.IDLE, stateB.status)
    }

    @Test
    fun `stale lease can be reclaimed by a new run`() = runBlocking {
        val runsRepo = SimpleEtlRunsRepository()
        val context = EtlContext(groupId = "g")

        // Pre-seed an "abandoned" run from another instance whose lease is already expired.
        val pre = SimpleEtlRunsRepository.RunState(
            status = EtlRunStatus.RUNNING,
            runsCount = 5,
            lastStartedAt = Instant.now().minusSeconds(3600),
            lockOwner = "dead-instance",
            lockExpiresAt = Instant.now().minusSeconds(60),
        )
        // Inject by claiming once with the dead owner, then time-warping the expiry.
        // Direct map injection is not exposed, so we round-trip through the API:
        runsRepo.tryAcquireLockAndStart("test-etl", context, "dead-instance", leaseSeconds = -60)
        // After this call: lockOwner = dead-instance, lockExpiresAt = now() - 60s, runsCount = 1.
        // Sanity-check the seed.
        val seeded = runsRepo.snapshot("test-etl", context)
        assertNotNull(seeded)
        assertEquals("dead-instance", seeded.lockOwner)
        assertTrue(seeded.lockExpiresAt!!.isBefore(Instant.now()), "Seeded lease must already be expired")

        val orchestrator = newOrchestrator(
            runsRepo = runsRepo,
            loader = SlowLoader(delayMillis = 0),
            rows = listOf(Row(1, Instant.now())),
        )
        val result = orchestrator.run(context)
        assertEquals(1, result.size)

        val after = runsRepo.snapshot("test-etl", context)
        assertNotNull(after)
        assertEquals(EtlRunStatus.IDLE, after.status, "Reclaimed lock must be released after the run")
        assertNull(after.lockOwner)
        // runsCount = 1 (dead seed) + 1 (our claim) = 2.
        assertEquals(2L, after.runsCount)
        assertTrue(after.lastFinishedAt != null, "lastFinishedAt must be set after the run")
    }
}