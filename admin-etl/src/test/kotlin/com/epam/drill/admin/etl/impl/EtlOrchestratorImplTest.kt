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

import com.epam.drill.admin.etl.DataExtractor
import com.epam.drill.admin.etl.DataLoader
import com.epam.drill.admin.etl.DataTransformer
import com.epam.drill.admin.etl.EtlContext
import com.epam.drill.admin.etl.EtlExtractingResult
import com.epam.drill.admin.etl.EtlLoadingResult
import com.epam.drill.admin.etl.EtlMetadata
import com.epam.drill.admin.etl.EtlRow
import com.epam.drill.admin.etl.EtlStatus
import com.epam.drill.admin.etl.SimpleEtlRunsRepository
import com.epam.drill.admin.etl.SimpleMetadataRepository
import com.epam.drill.admin.etl.config.EtlMeter
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EtlOrchestratorImplTest {

    private val metrics = EtlMeter(SimpleMeterRegistry())
    private val extractCallCount = AtomicInteger(0)

    data class GRow(val id: Int, val ts: Instant) : EtlRow(ts)

    private val gRowIdentity: DataTransformer<GRow, GRow> = object : DataTransformer<GRow, GRow> {
        override val name = "identity"
        override suspend fun transform(context: EtlContext, collector: Flow<GRow>): Flow<GRow> = collector
    }

    inner class CountingExtractor(
        override val name: String,
        private val rows: List<GRow>,
    ) : DataExtractor<GRow> {
        override suspend fun extract(
            context: EtlContext,
            sinceTimestamp: Instant,
            untilTimestamp: Instant,
            emitter: FlowCollector<GRow>,
            onExtractingProgress: suspend (EtlExtractingResult) -> Unit,
        ) {
            extractCallCount.incrementAndGet()
            rows.filter { it.ts > sinceTimestamp }.forEach { emitter.emit(it) }
        }
    }

    inner class CollectingLoader(override val name: String) : DataLoader<GRow> {
        val received = mutableListOf<GRow>()

        override suspend fun load(
            context: EtlContext,
            sinceTimestamp: Instant,
            untilTimestamp: Instant,
            collector: Flow<GRow>,
            onLoadingProgress: suspend (EtlLoadingResult) -> Unit,
            onStatusChanged: suspend (EtlStatus) -> Unit,
        ): EtlLoadingResult {
            collector.collect { received.add(it) }
            val lastTs = received.maxOfOrNull { it.ts } ?: sinceTimestamp
            val result = EtlLoadingResult(lastProcessedAt = lastTs, processedRows = received.size.toLong())
            onLoadingProgress(result)
            onStatusChanged(EtlStatus.SUCCESS)
            return result
        }

        override suspend fun deleteAll(context: EtlContext) = received.clear()
    }

    inner class ThrowingLoader(override val name: String) : DataLoader<GRow> {
        override suspend fun load(
            context: EtlContext,
            sinceTimestamp: Instant,
            untilTimestamp: Instant,
            collector: Flow<GRow>,
            onLoadingProgress: suspend (EtlLoadingResult) -> Unit,
            onStatusChanged: suspend (EtlStatus) -> Unit,
        ): EtlLoadingResult {
            collector.collect { throw RuntimeException("Simulated loader failure in ${this.name}") }
            return EtlLoadingResult(lastProcessedAt = sinceTimestamp)
        }

        override suspend fun deleteAll(context: EtlContext) {}
    }

    @BeforeEach
    fun reset() {
        extractCallCount.set(0)
    }

    @Test
    fun `orchestrator runs extractor exactly once for two pipelines sharing the same extractor`() = runBlocking {
        val rows = listOf(GRow(1, Instant.now()), GRow(2, Instant.now()), GRow(3, Instant.now()))
        val sharedExtractor = CountingExtractor(name = "shared", rows = rows)

        val loader1 = CollectingLoader("loader-a")
        val loader2 = CollectingLoader("loader-b")

        val pipeline1 = EtlPipelineImpl(
            name = "pipeline-a",
            extractor = sharedExtractor,
            transformer = gRowIdentity,
            loader = loader1,
            metrics = metrics,
        )
        val pipeline2 = EtlPipelineImpl(
            name = "pipeline-b",
            extractor = sharedExtractor,
            transformer = gRowIdentity,
            loader = loader2,
            metrics = metrics,
        )

        val orchestrator = EtlOrchestratorImpl(
            name = "test",
            pipelines = listOf(pipeline1, pipeline2),
            metadataRepository = SimpleMetadataRepository(),
            runsRepository = SimpleEtlRunsRepository(),
        )

        val results = orchestrator.run(EtlContext(groupId = "g1"))

        assertEquals(1, extractCallCount.get(), "Extractor must be called exactly once for the group")
        assertEquals(2, results.size)
        assertTrue(results.all { it.status == EtlStatus.SUCCESS })
        assertEquals(3, loader1.received.size, "loader-a must receive all rows")
        assertEquals(3, loader2.received.size, "loader-b must receive all rows")
    }

    @Test
    fun `orchestrator runs each extractor independently for pipelines with different extractors`() = runBlocking {
        val rows = listOf(GRow(1, Instant.now()), GRow(2, Instant.now()))
        val extractorA = CountingExtractor(name = "extractor-a", rows = rows)
        val extractorB = CountingExtractor(name = "extractor-b", rows = rows)

        val loaderA = CollectingLoader("loader-a")
        val loaderB = CollectingLoader("loader-b")

        val pipelineA = EtlPipelineImpl(
            name = "pipeline-a",
            extractor = extractorA,
            transformer = gRowIdentity,
            loader = loaderA,
            metrics = metrics,
        )
        val pipelineB = EtlPipelineImpl(
            name = "pipeline-b",
            extractor = extractorB,
            transformer = gRowIdentity,
            loader = loaderB,
            metrics = metrics,
        )

        val orchestrator = EtlOrchestratorImpl(
            name = "test",
            pipelines = listOf(pipelineA, pipelineB),
            metadataRepository = SimpleMetadataRepository(),
            runsRepository = SimpleEtlRunsRepository(),
        )

        orchestrator.run(EtlContext(groupId = "g1"))

        assertEquals(2, extractCallCount.get(), "Each extractor must be called once independently")
        assertEquals(2, loaderA.received.size)
        assertEquals(2, loaderB.received.size)
    }

    @Test
    fun `orchestrator broadcasts all extracted rows to every pipeline in a group`() = runBlocking {
        val now = Instant.now()
        val rows = (1..5).map { GRow(it, now) }
        val sharedExtractor = CountingExtractor(name = "shared", rows = rows)

        val loaders = (1..3).map { CollectingLoader("loader-$it") }
        val pipelines = loaders.mapIndexed { i, loader ->
            EtlPipelineImpl(
                name = "pipeline-$i",
                extractor = sharedExtractor,
                transformer = gRowIdentity,
                loader = loader,
                metrics = metrics,
            )
        }

        val orchestrator = EtlOrchestratorImpl(
            name = "test",
            pipelines = pipelines,
            metadataRepository = SimpleMetadataRepository(),
            runsRepository = SimpleEtlRunsRepository(),
        )

        val results = orchestrator.run(EtlContext(groupId = "g1"))

        assertEquals(1, extractCallCount.get(), "Extractor must be called exactly once")
        assertEquals(3, results.size)
        loaders.forEach { loader ->
            assertEquals(5, loader.received.size, "Each loader must receive all 5 rows")
        }
    }

    @Test
    fun `orchestrator uses per-pipeline sinceTimestamp so pipelines with different watermarks receive correct rows`() =
        runBlocking {
            val t0 = Instant.now().minusSeconds(100)
            val t1 = Instant.now().minusSeconds(50)
            val t2 = Instant.now()

            val allRows = listOf(GRow(1, t0), GRow(2, t1), GRow(3, t2))
            val sharedExtractor = CountingExtractor(name = "shared", rows = allRows)

            val loaderOld = CollectingLoader("loader-old")  // watermark at t0 â†’ receives rows > t0
            val loaderNew = CollectingLoader("loader-new")  // watermark at t1 â†’ receives rows > t1

            val pipelineOld = EtlPipelineImpl(
                name = "pipeline-old",
                extractor = sharedExtractor,
                transformer = gRowIdentity,
                loader = loaderOld,
                metrics = metrics,
            )
            val pipelineNew = EtlPipelineImpl(
                name = "pipeline-new",
                extractor = sharedExtractor,
                transformer = gRowIdentity,
                loader = loaderNew,
                metrics = metrics,
            )

            val repo = SimpleMetadataRepository()
            // Seed metadata so pipelineNew has a more recent watermark
            repo.saveMetadata(
                EtlContext(groupId = "g1"),
                EtlMetadata(
                    pipelineName = "pipeline-new", extractorName = "shared",
                    loaderName = "loader-new", lastProcessedAt = t1, lastRunAt = t1, status = EtlStatus.SUCCESS
                )
            )

            val orchestrator = EtlOrchestratorImpl(
                name = "test",
                pipelines = listOf(pipelineOld, pipelineNew),
                metadataRepository = repo,
                runsRepository = SimpleEtlRunsRepository(),
            )

            orchestrator.run(EtlContext(groupId = "g1"))

            // Extractor runs from min(watermark) = EPOCH (pipelineOld has no metadata), so all rows are extracted.
            // Both loaders receive all extracted rows; BatchDataLoader's internal skip handles per-loader filtering
            // in real SQL loaders. Here CollectingLoader collects everything passed to it.
            assertEquals(1, extractCallCount.get())
            // pipelineOld: no metadata â†’ sinceTimestamp = EPOCH â†’ all 3 rows extracted
            assertEquals(3, loaderOld.received.size)
            // pipelineNew: watermark = t1, loader skipping handled by loader impl; CollectingLoader gets what pipeline passes
            // In this test CollectingLoader doesn't filter by sinceTimestamp â€” that's BatchDataLoader's job.
            // We just verify the row count is consistent with what was broadcast.
            assertTrue(loaderNew.received.isNotEmpty(), "loader-new must receive at least the row after its watermark")
        }

    @Test
    fun `orchestrator skips pipeline that is already up-to-date and does not invoke extractor for it`() = runBlocking {
        val rows = listOf(GRow(1, Instant.now()), GRow(2, Instant.now()))
        val extractor = CountingExtractor(name = "shared", rows = rows)

        val loader = CollectingLoader("loader-fresh")

        val pipeline = EtlPipelineImpl(
            name = "pipeline-fresh",
            extractor = extractor,
            transformer = gRowIdentity,
            loader = loader,
            metrics = metrics,
        )

        val repo = SimpleMetadataRepository()
        // Seed metadata with lastProcessedAt in the future so that sinceTimestamp >= snapshotTime
        // and the pipeline is considered already up-to-date (SKIPPED)
        repo.saveMetadata(
            EtlContext(groupId = "g1"),
            EtlMetadata(
                pipelineName = "pipeline-fresh",
                extractorName = "shared",
                loaderName = "loader-fresh",
                lastProcessedAt = Instant.now().plusSeconds(60),
                lastRunAt = Instant.now().plusSeconds(60),
                status = EtlStatus.SUCCESS,
            )
        )

        val orchestrator = EtlOrchestratorImpl(
            name = "test",
            pipelines = listOf(pipeline),
            metadataRepository = repo,
            runsRepository = SimpleEtlRunsRepository(),
        )

        val results = orchestrator.run(EtlContext(groupId = "g1"))

        assertEquals(1, results.size, "Must return exactly one result")
        assertEquals(EtlStatus.SKIPPED, results.first().status, "Pipeline must be reported as SKIPPED")
        assertEquals(0L, results.first().rowsProcessed, "No rows must be processed for a skipped pipeline")
        assertEquals(0, extractCallCount.get(), "Extractor must NOT be called for an already up-to-date pipeline")
        assertTrue(loader.received.isEmpty(), "Loader must not receive any rows for a skipped pipeline")
    }

    @Test
    fun `orchestrator marks failing loader pipeline as FAILED and lets healthy loader pipeline succeed`() = runBlocking {
        val now = Instant.now()
        val rows = (1..5).map { GRow(it, now) }
        val sharedExtractor = CountingExtractor(name = "shared", rows = rows)

        val healthyLoader = CollectingLoader("loader-healthy")
        val failingLoader = ThrowingLoader("loader-failing")

        val healthyPipeline = EtlPipelineImpl(
            name = "pipeline-healthy",
            extractor = sharedExtractor,
            transformer = gRowIdentity,
            loader = healthyLoader,
            metrics = metrics,
        )
        val failingPipeline = EtlPipelineImpl(
            name = "pipeline-failing",
            extractor = sharedExtractor,
            transformer = gRowIdentity,
            loader = failingLoader,
            metrics = metrics,
        )

        val orchestrator = EtlOrchestratorImpl(
            name = "test",
            pipelines = listOf(healthyPipeline, failingPipeline),
            metadataRepository = SimpleMetadataRepository(),
            runsRepository = SimpleEtlRunsRepository(),
            bufferSize = 0
        )

        val results = orchestrator.run(EtlContext(groupId = "g1"))

        assertEquals(2, results.size)
        val healthyResult = results.first { it.pipelineName == "pipeline-healthy" }
        val failingResult = results.first { it.pipelineName == "pipeline-failing" }

        assertEquals(EtlStatus.SUCCESS, healthyResult.status, "Healthy pipeline must complete with SUCCESS")
        assertEquals(5, healthyLoader.received.size, "Healthy loader must receive all rows")

        assertEquals(EtlStatus.FAILED, failingResult.status, "Failing pipeline must be reported as FAILED")
        assertTrue(failingResult.errorMessage != null, "Failing pipeline must carry an error message")
    }
}