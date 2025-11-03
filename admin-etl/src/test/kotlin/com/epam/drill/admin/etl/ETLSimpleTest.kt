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

import com.epam.drill.admin.etl.impl.EtlPipelineImpl
import com.epam.drill.admin.etl.impl.EtlOrchestratorImpl
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

data class SimpleClass(val id: Int, val createdAt: Instant)

private const val SIMPLE_PIPELINE = "simple-pipeline"
private const val SIMPLE_EXTRACTOR = "simple-extractor"
private const val SIMPLE_LOADER = "simple-loader"
private const val FAILING_LOADER = "failing-loader"

class ETLSimpleTest {
    private val dataStore = mutableListOf<SimpleClass>()
    private val idSequence = AtomicInteger()
    private fun addNewRecords(count: Int) {
        repeat(count) { i ->
            dataStore.add(SimpleClass(idSequence.incrementAndGet(), Instant.now()))
        }
    }

    inner class SimpleExtractor : DataExtractor<SimpleClass> {
        override val name = SIMPLE_EXTRACTOR
        override suspend fun extract(sinceTimestamp: Instant, untilTimestamp: Instant, batchSize: Int): Iterator<SimpleClass> {
            return dataStore.filter { it.createdAt > sinceTimestamp }.iterator()
        }
    }

    inner class SimpleLoader : DataLoader<SimpleClass> {
        override val name = SIMPLE_LOADER
        override suspend fun load(data: Iterator<SimpleClass>, batchSize: Int): DataLoader.LoadResult {
            val list = data.asSequence().toList()
            return DataLoader.LoadResult(success = true, lastProcessedAt = list.last().createdAt, processedRows = list.count())
        }
    }

    inner class FailingLoader : DataLoader<SimpleClass> {
        override val name = FAILING_LOADER
        override suspend fun load(data: Iterator<SimpleClass>, batchSize: Int): DataLoader.LoadResult {
            throw RuntimeException("Simulated loader failure")
        }
    }

    inner class SimplePipelineImpl(
        override val name: String = SIMPLE_PIPELINE,
        override val extractor: DataExtractor<SimpleClass> = SimpleExtractor(),
        override val loaders: List<DataLoader<SimpleClass>> = listOf(SimpleLoader())
    ) : EtlPipelineImpl<SimpleClass>(name, extractor, loaders)

    inner class SimpleMetadataRepository : EtlMetadataRepository {
        private var metadata = EtlMetadata(
            pipelineName = SIMPLE_PIPELINE,
            lastProcessedAt = Instant.EPOCH,
            lastRunAt = Instant.EPOCH,
            duration = 0,
            status = EtlStatus.SUCCESS,
            rowsProcessed = 0,
            errorMessage = null,
            extractorName = SIMPLE_EXTRACTOR,
            loaderName = SIMPLE_LOADER
        )

        override suspend fun getMetadata(
            pipelineName: String,
            extractorName: String,
            loaderName: String
        ): EtlMetadata? = metadata.copy(
            pipelineName = pipelineName,
            extractorName = extractorName,
            loaderName = loaderName
        )

        override suspend fun saveMetadata(metadata: EtlMetadata) {
            this@SimpleMetadataRepository.metadata = metadata
        }

        override suspend fun getAllMetadataByExtractor(
            pipelineName: String,
            extractorName: String
        ): List<EtlMetadata> = listOf(metadata).filter { it.extractorName == extractorName && it.pipelineName == pipelineName }
    }

    inner class SimpleOrchestrator(
        override val pipelines: List<EtlPipeline<SimpleClass>> = listOf(SimplePipelineImpl()),
        override val metadataRepository: EtlMetadataRepository = SimpleMetadataRepository()
    ) : EtlOrchestratorImpl(pipelines, metadataRepository)

    @BeforeEach
    fun setUp() {
        dataStore.clear()
        idSequence.set(0)
    }

    @Test
    fun `given success loading, ETL orchestrator should move lastProcessedAt forward`() = runBlocking {
        val orchestrator = SimpleOrchestrator()

        // Get initial lastProcessedAt timestamp
        val initialMetadata = orchestrator.metadataRepository.getMetadata(SIMPLE_PIPELINE, SIMPLE_EXTRACTOR, SIMPLE_LOADER)!!
        val initialLastProcessedAt = initialMetadata.lastProcessedAt

        // Add some data
        addNewRecords(3)

        // Run ETL
        val result = orchestrator.runAll()

        // Verify ETL was successful
        assertTrue(result.first().success)
        assertEquals(3, result.first().rowsProcessed)

        // Get updated metadata and verify lastProcessedAt moved forward
        val updatedMetadata = orchestrator.metadataRepository.getMetadata(SIMPLE_PIPELINE, SIMPLE_EXTRACTOR, SIMPLE_LOADER)!!
        val updatedLastProcessedAt = updatedMetadata.lastProcessedAt

        assertTrue(updatedLastProcessedAt > initialLastProcessedAt)
        assertEquals(EtlStatus.SUCCESS, updatedMetadata.status)
    }

    @Test
    fun `given failed loading, ETL orchestrator should leave lastProcessedAt as initial`() = runBlocking {
        val orchestrator = SimpleOrchestrator(listOf(SimplePipelineImpl(
            loaders = listOf(FailingLoader())
        )))

        // Get initial lastProcessedAt timestamp (should be EPOCH)
        val initialMetadata = orchestrator.metadataRepository.getMetadata(SIMPLE_PIPELINE, SIMPLE_EXTRACTOR, FAILING_LOADER)!!
        val initialLastProcessedAt = initialMetadata.lastProcessedAt

        // Add some data
        addNewRecords(3)

        // Run ETL - should fail
        val result = orchestrator.runAll()

        // Verify ETL failed
        assertTrue(!result.first().success)
        assertEquals(0, result.first().rowsProcessed)

        // Get updated metadata and verify lastProcessedAt remained unchanged
        val updatedMetadata = orchestrator.metadataRepository.getMetadata(SIMPLE_PIPELINE, SIMPLE_EXTRACTOR, FAILING_LOADER)!!
        val updatedLastProcessedAt = updatedMetadata.lastProcessedAt

        assertEquals(initialLastProcessedAt, updatedLastProcessedAt)
        assertEquals(EtlStatus.FAILURE, updatedMetadata.status)
    }

    @Test
    fun `given several launches, ETL orchestrator should process only new data`() = runBlocking {
        val orchestrator = SimpleOrchestrator()

        // Add initial data
        addNewRecords(5)
        // First run ETL — should process all initial data
        val result1 = orchestrator.runAll()
        assertTrue(result1.first().success)
        assertEquals(5, result1.first().rowsProcessed)

        Thread.sleep(10)
        // Add new data after last processed timestamp
        addNewRecords(3)
        // Second run ETL — should process only new data
        val result2 = orchestrator.runAll()
        println(result2.first().errorMessage)
        assertTrue(result2.first().success)
        assertEquals(3, result2.first().rowsProcessed)
    }
}
