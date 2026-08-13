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

import com.epam.drill.admin.etl.config.EtlMeter
import com.epam.drill.admin.etl.impl.EtlPipelineImpl
import com.epam.drill.admin.etl.impl.EtlOrchestratorImpl
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

data class SimpleClass(val id: Int, val createdAt: Instant) : EtlRow(createdAt)

private const val SIMPLE_PIPELINE = "simple-pipeline"
private const val SIMPLE_EXTRACTOR = "simple-extractor"
private const val SIMPLE_LOADER = "simple-loader"
private const val SIMPLE_TRANSFORMER = "simple-transformer"
private const val FAILING_LOADER = "failing-loader"

class ETLSimpleTest {
    private val dataStore = mutableListOf<SimpleClass>()
    private val idSequence = AtomicInteger()
    private val metrics: EtlMeter = EtlMeter(SimpleMeterRegistry())
    private fun addNewRecords(count: Int) {
        repeat(count) {
            dataStore.add(SimpleClass(idSequence.incrementAndGet(), Instant.now()))
        }
    }

    inner class SimpleExtractor : DataExtractor<SimpleClass> {
        override val name = SIMPLE_EXTRACTOR
        override suspend fun extract(
            context: EtlContext,
            sinceTimestamp: Instant,
            untilTimestamp: Instant,
            emitter: FlowCollector<SimpleClass>,
            onExtractingProgress: suspend (EtlExtractingResult) -> Unit
        ) {
            dataStore.filter { it.createdAt > sinceTimestamp }.forEach { emitter.emit(it) }
        }
    }

    inner class SimpleLoader : DataLoader<SimpleClass> {
        override val name = SIMPLE_LOADER
        override suspend fun load(
            context: EtlContext,
            sinceTimestamp: Instant,
            untilTimestamp: Instant,
            collector: Flow<SimpleClass>,
            onLoadingProgress: suspend (EtlLoadingResult) -> Unit,
            onStatusChanged: suspend (EtlStatus) -> Unit
        ): EtlLoadingResult {
            var lastExtracted: SimpleClass? = null
            var rowsProcessed = 0L
            collector.collect {
                lastExtracted = it
                rowsProcessed++
            }
            return EtlLoadingResult(
                lastProcessedAt = lastExtracted?.createdAt ?: sinceTimestamp,
                processedRows = rowsProcessed
            ).also {
                onLoadingProgress(it)
                onStatusChanged(EtlStatus.SUCCESS)
            }
        }

        override suspend fun deleteAll(context: EtlContext, period: EtlPeriod) {
            dataStore.clear()
        }
    }

    inner class SimpleTransformer : DataTransformer<SimpleClass, SimpleClass> {
        override val name = SIMPLE_TRANSFORMER
        override suspend fun transform(
            context: EtlContext,
            collector: Flow<SimpleClass>
        ): Flow<SimpleClass> = flow {
            collector.collect { emit(it) }
        }
    }

    inner class FailingLoader : DataLoader<SimpleClass> {
        override val name = FAILING_LOADER
        override suspend fun load(
            context: EtlContext,
            sinceTimestamp: Instant,
            untilTimestamp: Instant,
            collector: Flow<SimpleClass>,
            onLoadingProgress: suspend (EtlLoadingResult) -> Unit,
            onStatusChanged: suspend (EtlStatus) -> Unit
        ): EtlLoadingResult {
            collector.collect {
                throw RuntimeException("Simulated loader failure")
            }
            return EtlLoadingResult(
                errorMessage = "This should never be returned",
                lastProcessedAt = sinceTimestamp
            )
        }

        override suspend fun deleteAll(context: EtlContext, period: EtlPeriod) {
            dataStore.clear()
        }
    }


    private fun buildOrchestrator(
        metadataRepository: EtlMetadataRepository = SimpleMetadataRepository(),
        jobsRepository: EtlJobsRepository = SimpleEtlJobsRepository()
    ) = EtlOrchestratorImpl(
        name = "simple-etl",
        pipelines = listOf(
            EtlPipelineImpl(
                name = SIMPLE_PIPELINE,
                extractor = SimpleExtractor(),
                transformer = SimpleTransformer(),
                loader = SimpleLoader(),
                metrics = metrics,
            )
        ),
        metadataRepository = metadataRepository,
        jobsRepository = jobsRepository
    )

    private suspend fun runIncremental(
        orchestrator: EtlOrchestratorImpl,
        jobsRepository: EtlJobsRepository,
        context: EtlContext,
    ): EtlJobResult {
        val job = jobsRepository.findResumable(orchestrator.name, context, EtlPeriod.UNBOUNDED).firstOrNull()
            ?: jobsRepository.scheduleJob(orchestrator.name, context, EtlPeriod.UNBOUNDED)
            ?: error("Could not schedule/resume job")
        return orchestrator.run(job, "test-worker")
    }

    @BeforeEach
    fun setUp() {
        dataStore.clear()
        idSequence.set(0)
    }

    @Test
    fun `given success loading, ETL orchestrator should move lastProcessedAt forward`() = runBlocking {
        val repo = SimpleMetadataRepository()
        val jobsRepository = SimpleEtlJobsRepository()
        val orchestrator = buildOrchestrator(repo, jobsRepository)
        val context = EtlContext(groupId = "test-group")

        val initialLastProcessedAt = repo.getMetadata(context, SIMPLE_PIPELINE)
            ?.lastProcessedAt ?: Instant.EPOCH

        addNewRecords(3)
        val result = runIncremental(orchestrator, jobsRepository, context)

        // Unbounded (incremental) periods never "complete" - IDLE means the worker
        // caught up with all available data but the period stays open for future runs.
        assertTrue(result.status == EtlJobStatus.IDLE)

        val updatedLastProcessedAt = repo.getMetadata(context, SIMPLE_PIPELINE)
            ?.lastProcessedAt ?: Instant.EPOCH

        assertTrue(updatedLastProcessedAt > initialLastProcessedAt)
        assertEquals(EtlStatus.SUCCESS, repo.getMetadata(context, SIMPLE_PIPELINE)?.status)
    }

    @Test
    fun `given failed loading, ETL orchestrator should leave lastProcessedAt as initial`() = runBlocking {
        val context = EtlContext(groupId = "test-group")
        val metadataRepo = SimpleMetadataRepository()
        val jobsRepository = SimpleEtlJobsRepository()
        val orchestrator = EtlOrchestratorImpl(
            name = "failed-etl",
            pipelines = listOf(
                EtlPipelineImpl(
                    name = "failed-pipeline",
                    extractor = SimpleExtractor(),
                    transformer = SimpleTransformer(),
                    loader = FailingLoader(),
                    metrics = metrics,
                )
            ),
            metadataRepository = metadataRepo,
            jobsRepository = jobsRepository
        )

        val initialLastProcessedAt = metadataRepo.getMetadata(context, "failed-pipeline")
            ?.lastProcessedAt ?: Instant.EPOCH

        addNewRecords(3)
        val result = runIncremental(orchestrator, jobsRepository, context)

        assertTrue(result.status == EtlJobStatus.ERROR)

        val updatedLastProcessedAt = metadataRepo.getMetadata(context, "failed-pipeline")
            ?.lastProcessedAt ?: Instant.EPOCH

        assertEquals(initialLastProcessedAt, updatedLastProcessedAt)
        assertEquals(EtlStatus.FAILED, metadataRepo.getMetadata(context, "failed-pipeline")?.status)
    }

    @Test
    fun `given several launches, ETL orchestrator should process only new data`() = runBlocking {
        val repo = SimpleMetadataRepository()
        val jobsRepository = SimpleEtlJobsRepository()
        val orchestrator = buildOrchestrator(repo, jobsRepository)
        val groupId = "test-group"
        val context = EtlContext(groupId = groupId)

        addNewRecords(5)
        val result1 = runIncremental(orchestrator, jobsRepository, context)
        assertTrue(result1.status == EtlJobStatus.IDLE)

        Thread.sleep(10)
        addNewRecords(3)
        val result2 = runIncremental(orchestrator, jobsRepository, context)
        assertTrue(result2.status == EtlJobStatus.IDLE)
    }

    @Test
    fun `given consistencyWindow, ETL orchestrator should re-process records within the lookback window`() =
        runBlocking {
            val groupId = "test-group"
            val context = EtlContext(groupId = groupId)
            val metadataRepo = SimpleMetadataRepository()
            val jobsRepository = SimpleEtlJobsRepository()
            val orchestrator = EtlOrchestratorImpl(
                name = "lookback-etl",
                pipelines = listOf(
                    EtlPipelineImpl(
                        name = SIMPLE_PIPELINE,
                        extractor = SimpleExtractor(),
                        transformer = SimpleTransformer(),
                        loader = SimpleLoader(),
                        metrics = metrics,
                    )
                ),
                metadataRepository = metadataRepo,
                jobsRepository = jobsRepository,
                consistencyWindow = 60,
            )

            addNewRecords(5)
            val result1 = runIncremental(orchestrator, jobsRepository, context)
            assertTrue(result1.status == EtlJobStatus.IDLE)

            addNewRecords(3)
            // lookback of 60s should re-process all 8 records (5 original + 3 new)
            val result2 = runIncremental(orchestrator, jobsRepository, context)
            assertTrue(result2.status == EtlJobStatus.IDLE)
        }

    @Test
    fun `given bounded period reaching its end, ETL orchestrator should report COMPLETED`() = runBlocking {
        val repo = SimpleMetadataRepository()
        val jobsRepository = SimpleEtlJobsRepository()
        val orchestrator = buildOrchestrator(repo, jobsRepository)
        val context = EtlContext(groupId = "test-group")

        // A bounded period (today..today) has a non-null untilTimestamp, so once the
        // worker reaches it without errors, the job is reported as COMPLETED.
        val yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1)
        val period = EtlPeriod(from = yesterday, to = yesterday)

        addNewRecords(3)

        val job = jobsRepository.findResumable(orchestrator.name, context, period).firstOrNull()
            ?: jobsRepository.scheduleJob(orchestrator.name, context, period)
            ?: error("Could not schedule/resume job")
        val result = orchestrator.run(job, "test-worker")

        assertEquals(EtlJobStatus.COMPLETED, result.status)
        assertEquals(EtlStatus.SUCCESS, repo.getMetadata(context, SIMPLE_PIPELINE, period)?.status)
    }
}


