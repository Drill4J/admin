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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

data class SimpleClass(val id: Int, val createdAt: Instant): EtlRow(createdAt)

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
            groupId: String,
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
            groupId: String,
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

        override suspend fun deleteAll(groupId: String) {
            dataStore.clear()
        }
    }

    inner class SimpleTransformer : DataTransformer<SimpleClass, SimpleClass> {
        override val name = SIMPLE_TRANSFORMER
        override suspend fun transform(
            groupId: String,
            collector: Flow<SimpleClass>
        ): Flow<SimpleClass> = flow {
            collector.collect { emit(it) }
        }
    }

    inner class FailingLoader : DataLoader<SimpleClass> {
        override val name = FAILING_LOADER
        override suspend fun load(
            groupId: String,
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
                lastProcessedAt = sinceTimestamp)
        }

        override suspend fun deleteAll(groupId: String) {
            dataStore.clear()
        }
    }

    inner class SimpleMetadataRepository : EtlMetadataRepository {
        private val store = mutableMapOf<String, EtlMetadata>()

        private fun key(groupId: String, pipelineName: String, extractorName: String, loaderName: String) =
            "$groupId|$pipelineName|$extractorName|$loaderName"

        override suspend fun getMetadata(
            groupId: String,
            pipelineName: String,
            extractorName: String,
            loaderName: String
        ): EtlMetadata? = store[key(groupId, pipelineName, extractorName, loaderName)]

        override suspend fun saveMetadata(metadata: EtlMetadata) {
            store[key(metadata.groupId, metadata.pipelineName, metadata.extractorName, metadata.loaderName)] = metadata
        }

        override suspend fun deleteMetadataByPipeline(groupId: String, pipelineName: String) {
            store.keys.removeAll { it.startsWith("$groupId|$pipelineName|") }
        }

        override suspend fun getAllMetadataByExtractor(
            groupId: String,
            pipelineName: String,
            extractorName: String
        ): List<EtlMetadata> =
            store.values.filter {
                it.groupId == groupId && it.extractorName == extractorName && it.pipelineName == pipelineName
            }

        override suspend fun getAllMetadata(groupId: String): List<EtlMetadata> =
            store.values.filter { it.groupId == groupId }

        override suspend fun accumulateMetadataByLoader(
            groupId: String,
            pipelineName: String,
            extractorName: String,
            loaderName: String,
            lastProcessedAt: Instant?,
            status: EtlStatus?,
            loadDuration: Long,
            rowsProcessed: Long,
            errorMessage: String?
        ) {
            val k = key(groupId, pipelineName, extractorName, loaderName)
            val existing = store[k] ?: EtlMetadata(
                groupId = groupId, pipelineName = pipelineName, extractorName = extractorName,
                loaderName = loaderName, lastProcessedAt = Instant.EPOCH, lastRunAt = Instant.EPOCH,
                status = EtlStatus.SUCCESS
            )
            store[k] = existing.copy(
                lastProcessedAt = lastProcessedAt ?: existing.lastProcessedAt,
                lastLoadDuration = existing.lastLoadDuration + loadDuration,
                lastRowsProcessed = existing.lastRowsProcessed + rowsProcessed,
                status = status ?: (if (errorMessage != null) EtlStatus.FAILED else existing.status),
                errorMessage = errorMessage
            )
        }

        override suspend fun accumulateMetadataByExtractor(
            groupId: String,
            pipelineName: String,
            extractorName: String,
            status: EtlStatus?,
            extractDuration: Long,
            errorMessage: String?
        ) {
            store.entries
                .filter { (k, _) -> k.startsWith("$groupId|$pipelineName|$extractorName|") }
                .forEach { (k, existing) ->
                    store[k] = existing.copy(
                        lastExtractDuration = existing.lastExtractDuration + extractDuration,
                        status = if (errorMessage != null) EtlStatus.FAILED else existing.status,
                        errorMessage = errorMessage
                    )
                }
        }
    }

    private fun buildOrchestrator(metadataRepository: EtlMetadataRepository = SimpleMetadataRepository()) =
        EtlOrchestratorImpl(
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
        )

    @BeforeEach
    fun setUp() {
        dataStore.clear()
        idSequence.set(0)
    }

    @Test
    fun `given success loading, ETL orchestrator should move lastProcessedAt forward`() = runBlocking {
        val repo = SimpleMetadataRepository()
        val orchestrator = buildOrchestrator(repo)
        val groupId = "test-group"

        val initialLastProcessedAt = repo.getMetadata(groupId, SIMPLE_PIPELINE, SIMPLE_EXTRACTOR, SIMPLE_LOADER)
            ?.lastProcessedAt ?: Instant.EPOCH

        addNewRecords(3)
        val result = orchestrator.run(groupId)

        assertTrue(result.first().status == EtlStatus.SUCCESS)
        assertEquals(3, result.first().rowsProcessed)

        val updatedLastProcessedAt = repo.getMetadata(groupId, SIMPLE_PIPELINE, SIMPLE_EXTRACTOR, SIMPLE_LOADER)
            ?.lastProcessedAt ?: Instant.EPOCH

        assertTrue(updatedLastProcessedAt > initialLastProcessedAt)
        assertEquals(EtlStatus.SUCCESS, repo.getMetadata(groupId, SIMPLE_PIPELINE, SIMPLE_EXTRACTOR, SIMPLE_LOADER)?.status)
    }

    @Test
    fun `given failed loading, ETL orchestrator should leave lastProcessedAt as initial`() = runBlocking {
        val groupId = "test-group"
        val repo = SimpleMetadataRepository()
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
            metadataRepository = repo,
        )

        val initialLastProcessedAt = repo.getMetadata(groupId, "failed-pipeline", SIMPLE_EXTRACTOR, FAILING_LOADER)
            ?.lastProcessedAt ?: Instant.EPOCH

        addNewRecords(3)
        val result = orchestrator.run(groupId)

        assertTrue(result.first().status == EtlStatus.FAILED)
        assertEquals(0, result.first().rowsProcessed)

        val updatedLastProcessedAt = repo.getMetadata(groupId, "failed-pipeline", SIMPLE_EXTRACTOR, FAILING_LOADER)
            ?.lastProcessedAt ?: Instant.EPOCH

        assertEquals(initialLastProcessedAt, updatedLastProcessedAt)
        assertEquals(EtlStatus.FAILED, repo.getMetadata(groupId, "failed-pipeline", SIMPLE_EXTRACTOR, FAILING_LOADER)?.status)
    }

    @Test
    fun `given several launches, ETL orchestrator should process only new data`() = runBlocking {
        val repo = SimpleMetadataRepository()
        val orchestrator = buildOrchestrator(repo)
        val groupId = "test-group"

        addNewRecords(5)
        val result1 = orchestrator.run(groupId)
        assertTrue(result1.first().status == EtlStatus.SUCCESS)
        assertEquals(5, result1.first().rowsProcessed)

        Thread.sleep(10)
        addNewRecords(3)
        val result2 = orchestrator.run(groupId)
        assertTrue(result2.first().status == EtlStatus.SUCCESS)
        assertEquals(3, result2.first().rowsProcessed)
    }

    @Test
    fun `given consistencyWindow, ETL orchestrator should re-process records within the lookback window`() = runBlocking {
        val groupId = "test-group"
        val repo = SimpleMetadataRepository()
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
            metadataRepository = repo,
            consistencyWindow = 60,
        )

        addNewRecords(5)
        val result1 = orchestrator.run(groupId)
        assertTrue(result1.first().status == EtlStatus.SUCCESS)
        assertEquals(5, result1.first().rowsProcessed)

        addNewRecords(3)
        // lookback of 60s should re-process all 8 records (5 original + 3 new)
        val result2 = orchestrator.run(groupId)
        assertTrue(result2.first().status == EtlStatus.SUCCESS)
        assertEquals(8, result2.first().rowsProcessed)
    }
}


