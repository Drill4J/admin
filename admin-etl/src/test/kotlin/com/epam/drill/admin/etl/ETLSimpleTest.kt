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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
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
        override suspend fun extract(
            groupId: String,
            sinceTimestamp: Instant,
            untilTimestamp: Instant,
            emitter: FlowCollector<SimpleClass>,
            onExtractCompleted: suspend (EtlExtractingResult) -> Unit
        ) {
            return dataStore.filter { it.createdAt > sinceTimestamp }.forEach { emitter.emit(it) }
        }
    }

    inner class SimpleLoader : DataLoader<SimpleClass> {
        override val name = SIMPLE_LOADER
        override suspend fun load(
            groupId: String,
            sinceTimestamp: Instant,
            untilTimestamp: Instant,
            collector: Flow<SimpleClass>,
            onLoadCompleted: suspend (EtlLoadingResult) -> Unit
        ): EtlLoadingResult {
            var lastExtracted: SimpleClass? = null
            var rowsProcessed = 0L
            collector.collect {
                lastExtracted = it
                rowsProcessed++
            }
            return EtlLoadingResult(
                status = EtlStatus.SUCCESS,
                lastProcessedAt = lastExtracted?.createdAt ?: sinceTimestamp,
                processedRows = rowsProcessed
            ).also {
                onLoadCompleted(it)
            }
        }

        override suspend fun deleteAll(groupId: String) {
            dataStore.clear()
        }
    }

    inner class FailingLoader : DataLoader<SimpleClass> {
        override val name = FAILING_LOADER
        override suspend fun load(
            groupId: String,
            sinceTimestamp: Instant,
            untilTimestamp: Instant,
            collector: Flow<SimpleClass>,
            onLoadCompleted: suspend (EtlLoadingResult) -> Unit
        ): EtlLoadingResult {
            collector.collect {
                throw RuntimeException("Simulated loader failure")
            }
            return EtlLoadingResult(
                status = EtlStatus.FAILED,
                errorMessage = "This should never be returned",
                lastProcessedAt = sinceTimestamp)
        }

        override suspend fun deleteAll(groupId: String) {
            dataStore.clear()
        }
    }

    inner class SimpleMetadataRepository : EtlMetadataRepository {
        private var metadata = EtlMetadata(
            groupId = "test-group",
            pipelineName = SIMPLE_PIPELINE,
            lastProcessedAt = Instant.EPOCH,
            lastRunAt = Instant.EPOCH,
            lastDuration = 0,
            lastRowsProcessed = 0,
            status = EtlStatus.SUCCESS,
            errorMessage = null,
            extractorName = SIMPLE_EXTRACTOR,
            loaderName = SIMPLE_LOADER
        )

        override suspend fun getMetadata(
            groupId: String,
            pipelineName: String,
            extractorName: String,
            loaderName: String
        ): EtlMetadata? = metadata.copy(
            groupId = groupId,
            pipelineName = pipelineName,
            extractorName = extractorName,
            loaderName = loaderName
        )

        override suspend fun saveMetadata(metadata: EtlMetadata) {
            this@SimpleMetadataRepository.metadata = metadata
        }

        override suspend fun deleteMetadataByPipeline(groupId: String, pipelineName: String) {
            if (metadata.pipelineName == pipelineName && metadata.groupId == groupId) {
                metadata = EtlMetadata(
                    groupId = groupId,
                    pipelineName = pipelineName,
                    lastProcessedAt = Instant.EPOCH,
                    lastRunAt = Instant.EPOCH,
                    lastDuration = 0,
                    lastRowsProcessed = 0,
                    status = EtlStatus.SUCCESS,
                    errorMessage = null,
                    extractorName = SIMPLE_EXTRACTOR,
                    loaderName = SIMPLE_LOADER
                )
            }
        }

        override suspend fun getAllMetadataByExtractor(
            groupId: String,
            pipelineName: String,
            extractorName: String
        ): List<EtlMetadata> =
            listOf(metadata).filter { it.groupId == groupId && it.extractorName == extractorName && it.pipelineName == pipelineName }

        override suspend fun getAllMetadata(groupId: String): List<EtlMetadata> {
            return listOf(metadata).filter { it.groupId == groupId }
        }

        override suspend fun accumulateMetadata(metadata: EtlMetadata) {
            this.metadata = this.metadata.copy(
                lastProcessedAt = metadata.lastProcessedAt,
                lastRunAt = metadata.lastRunAt,
                lastDuration = this.metadata.lastDuration + metadata.lastDuration,
                lastRowsProcessed = this.metadata.lastRowsProcessed + metadata.lastRowsProcessed,
                status = metadata.status,
                errorMessage = metadata.errorMessage
            )
        }

        override suspend fun accumulateMetadataDurationByExtractor(
            groupId: String,
            pipelineName: String,
            extractorName: String,
            duration: Long
        ) {

        }
    }

    val simpleOrchestrator = EtlOrchestratorImpl(
        "simple-etl",
        listOf(
            EtlPipelineImpl(
                "simple-pipeline",
                extractor = SimpleExtractor(),
                loaders = listOf(SimpleLoader())
            )
        ),
        metadataRepository = SimpleMetadataRepository()
    )

    @BeforeEach
    fun setUp() {
        dataStore.clear()
        idSequence.set(0)
    }

    @Test
    fun `given success loading, ETL orchestrator should move lastProcessedAt forward`() = runBlocking {
        val orchestrator = simpleOrchestrator
        val groupId = "test-group"

        // Get initial lastProcessedAt timestamp
        val initialMetadata =
            orchestrator.metadataRepository.getMetadata(groupId, SIMPLE_PIPELINE, SIMPLE_EXTRACTOR, SIMPLE_LOADER)!!
        val initialLastProcessedAt = initialMetadata.lastProcessedAt

        // Add some data
        addNewRecords(3)

        // Run ETL
        val result = orchestrator.run(groupId)

        // Verify ETL was successful
        assertTrue(result.first().status == EtlStatus.SUCCESS)
        assertEquals(3, result.first().rowsProcessed)

        // Get updated metadata and verify lastProcessedAt moved forward
        val updatedMetadata =
            orchestrator.metadataRepository.getMetadata(groupId, SIMPLE_PIPELINE, SIMPLE_EXTRACTOR, SIMPLE_LOADER)!!
        val updatedLastProcessedAt = updatedMetadata.lastProcessedAt

        assertTrue(updatedLastProcessedAt > initialLastProcessedAt)
        assertEquals(EtlStatus.SUCCESS, updatedMetadata.status)
    }

    @Test
    fun `given failed loading, ETL orchestrator should leave lastProcessedAt as initial`() = runBlocking {
        val groupId = "test-group"
        val orchestrator = EtlOrchestratorImpl(
            "failed-etl",
            listOf(
                EtlPipelineImpl(
                    "failed-pipeline",
                    extractor = SimpleExtractor(),
                    loaders = listOf(FailingLoader())
                )
            ),
            metadataRepository = SimpleMetadataRepository()
        )

        // Get initial lastProcessedAt timestamp (should be EPOCH)
        val initialMetadata =
            orchestrator.metadataRepository.getMetadata(groupId, SIMPLE_PIPELINE, SIMPLE_EXTRACTOR, FAILING_LOADER)!!
        val initialLastProcessedAt = initialMetadata.lastProcessedAt

        // Add some data
        addNewRecords(3)

        // Run ETL - should fail
        val result = orchestrator.run(groupId)

        // Verify ETL failed
        assertTrue(result.first().status == EtlStatus.FAILED)
        assertEquals(0, result.first().rowsProcessed)

        // Get updated metadata and verify lastProcessedAt remained unchanged
        val updatedMetadata =
            orchestrator.metadataRepository.getMetadata(groupId, SIMPLE_PIPELINE, SIMPLE_EXTRACTOR, FAILING_LOADER)!!
        val updatedLastProcessedAt = updatedMetadata.lastProcessedAt

        assertEquals(initialLastProcessedAt, updatedLastProcessedAt)
        assertEquals(EtlStatus.FAILED, updatedMetadata.status)
    }

    @Test
    fun `given several launches, ETL orchestrator should process only new data`() = runBlocking {
        val orchestrator = simpleOrchestrator
        val groupId = "test-group"

        // Add initial data
        addNewRecords(5)
        // First run ETL — should process all initial data
        val result1 = orchestrator.run(groupId)
        assertTrue(result1.first().status == EtlStatus.SUCCESS)
        assertEquals(5, result1.first().rowsProcessed)

        Thread.sleep(10)
        // Add new data after last processed timestamp
        addNewRecords(3)
        // Second run ETL — should process only new data
        val result2 = orchestrator.run(groupId)
        println(result2.first().errorMessage)
        assertTrue(result2.first().status == EtlStatus.SUCCESS)
        assertEquals(3, result2.first().rowsProcessed)
    }
}
