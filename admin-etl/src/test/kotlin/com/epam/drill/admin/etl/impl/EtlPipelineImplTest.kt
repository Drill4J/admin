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
import com.epam.drill.admin.etl.EtlExtractingResult
import com.epam.drill.admin.etl.EtlLoadingResult
import com.epam.drill.admin.etl.EtlContext
import com.epam.drill.admin.etl.EtlRow
import com.epam.drill.admin.etl.EtlStatus
import com.epam.drill.admin.etl.config.EtlMeter
import com.epam.drill.admin.etl.flow.asClosableFlow
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EtlPipelineImplTest {

    @Test
    fun `execute should fail on out-of-order timestamps`() = runBlocking {
        val items = listOf(
            TestItem(Instant.ofEpochSecond(2), "item2"),
            TestItem(Instant.ofEpochSecond(1), "item1")
        )
        val loader = CapturingLoader()
        val pipeline = buildPipeline(loader)
        val progressResults = mutableListOf<EtlLoadingResult>()

        pipeline.execute(
            context = EtlContext(groupId = "test-group"),
            sinceTimestamp = Instant.EPOCH,
            untilTimestamp = Instant.now(),
            extractionFlow = items.asClosableFlow(),
            onLoadingProgress = { progressResults.add(it) }
        )

        assertTrue(
            progressResults.any { it.isFailed },
            "Expected onLoadingProgress to be called with a failed result due to out-of-order timestamps"
        )
        // Only item2 (t=2) should have been loaded before the violation was detected
        assertEquals(1, loader.loadedItems.size)
        assertEquals("item2", loader.loadedItems[0].data)
    }

    @Test
    fun `execute should skip already processed rows`() = runBlocking {
        val since = Instant.ofEpochSecond(5)
        val items = (1..10).map { TestItem(Instant.ofEpochSecond(it.toLong()), "item$it") }
        val loader = CapturingLoader()
        val pipeline = buildPipeline(loader)

        val result = pipeline.execute(
            context = EtlContext(groupId = "test-group"),
            sinceTimestamp = since,
            untilTimestamp = Instant.now(),
            extractionFlow = items.asClosableFlow()
        )

        assertEquals(EtlStatus.SUCCESS, result.status)
        assertEquals(5, result.rowsProcessed)
        assertEquals(5, loader.loadedItems.size)
        assertEquals("item6", loader.loadedItems[0].data)
    }

    @Test
    fun `execute should stop processing rows beyond untilTimestamp`() = runBlocking {
        val until = Instant.ofEpochSecond(5)
        val items = (1..10).map { TestItem(Instant.ofEpochSecond(it.toLong()), "item$it") }
        val loader = CapturingLoader()
        val pipeline = buildPipeline(loader)

        val result = pipeline.execute(
            context = EtlContext(groupId = "test-group"),
            sinceTimestamp = Instant.EPOCH,
            untilTimestamp = until,
            extractionFlow = items.asClosableFlow()
        )

        assertEquals(EtlStatus.SUCCESS, result.status)
        assertEquals(5, result.rowsProcessed)
        assertEquals(5, loader.loadedItems.size)
        assertEquals("item5", loader.loadedItems.last().data)
    }

    @Test
    fun `execute should return FAILED when loader throws exception`() = runBlocking {
        val items = (1..5).map { TestItem(Instant.ofEpochSecond(it.toLong()), "item$it") }
        val pipeline = buildPipelineWithThrowingLoader()
        val progressResults = mutableListOf<EtlLoadingResult>()

        val result = pipeline.execute(
            context = EtlContext(groupId = "test-group"),
            sinceTimestamp = Instant.EPOCH,
            untilTimestamp = Instant.now(),
            extractionFlow = items.asClosableFlow(),
            onLoadingProgress = { progressResults.add(it) }
        )

        assertEquals(EtlStatus.FAILED, result.status)
        assertTrue(result.errorMessage != null)
        assertEquals(Instant.EPOCH, result.lastProcessedAt)
        assertTrue(progressResults.any { it.isFailed })
    }

    @Test
    fun `execute should apply transformer to items before loading`() = runBlocking {
        val items = (1..3).map { TestItem(Instant.ofEpochSecond(it.toLong()), "item$it") }
        val loader = TransformedCapturingLoader()
        val pipeline = buildPipelineWithMappingTransformer(loader)

        val result = pipeline.execute(
            context = EtlContext(groupId = "test-group"),
            sinceTimestamp = Instant.EPOCH,
            untilTimestamp = Instant.now(),
            extractionFlow = items.asClosableFlow()
        )

        assertEquals(EtlStatus.SUCCESS, result.status)
        assertEquals(3, loader.loadedItems.size)
        assertTrue(loader.loadedItems.all { it.data.startsWith("transformed-") })
        assertEquals("transformed-item1", loader.loadedItems[0].data)
    }

    @Test
    fun `execute should handle empty flow`() = runBlocking {
        val loader = CapturingLoader()
        val pipeline = buildPipeline(loader)

        val result = pipeline.execute(
            context = EtlContext(groupId = "test-group"),
            sinceTimestamp = Instant.EPOCH,
            untilTimestamp = Instant.now(),
            extractionFlow = emptyList<TestItem>().asClosableFlow()
        )

        assertEquals(EtlStatus.SUCCESS, result.status)
        assertEquals(0, result.rowsProcessed)
        assertEquals(0, loader.loadedItems.size)
    }

    @Test
    fun `execute should call onStatusChanged with SUCCESS on successful processing`() = runBlocking {
        val items = (1..3).map { TestItem(Instant.ofEpochSecond(it.toLong()), "item$it") }
        val loader = CapturingLoader()
        val pipeline = buildPipeline(loader)
        val statusChanges = mutableListOf<EtlStatus>()

        pipeline.execute(
            context = EtlContext(groupId = "test-group"),
            sinceTimestamp = Instant.EPOCH,
            untilTimestamp = Instant.now(),
            extractionFlow = items.asClosableFlow(),
            onStatusChanged = { statusChanges.add(it) }
        )

        assertTrue(statusChanges.contains(EtlStatus.SUCCESS))
    }

    // --- helpers ---

    private class TestItem(timestamp: Instant, val data: String) : EtlRow(timestamp)

    private class TransformedItem(timestamp: Instant, val data: String) : EtlRow(timestamp)

    private class NoOpExtractor : DataExtractor<TestItem> {
        override val name = "noop-extractor"
        override suspend fun extract(
            context: EtlContext,
            sinceTimestamp: Instant,
            untilTimestamp: Instant,
            emitter: FlowCollector<TestItem>,
            onExtractingProgress: suspend (EtlExtractingResult) -> Unit
        ) {}
    }

    private class PassthroughTransformer : DataTransformer<TestItem, TestItem> {
        override val name = "passthrough-transformer"
        override suspend fun transform(context: EtlContext, collector: Flow<TestItem>): Flow<TestItem> = flow {
            collector.collect { emit(it) }
        }
    }

    private class PrefixingTransformer : DataTransformer<TestItem, TransformedItem> {
        override val name = "prefixing-transformer"
        override suspend fun transform(context: EtlContext, collector: Flow<TestItem>): Flow<TransformedItem> = flow {
            collector.collect { emit(TransformedItem(it.timestamp, "transformed-${it.data}")) }
        }
    }

    private class CapturingLoader : DataLoader<TestItem> {
        override val name = "capturing-loader"
        val loadedItems = mutableListOf<TestItem>()

        override suspend fun load(
            context: EtlContext,
            sinceTimestamp: Instant,
            untilTimestamp: Instant,
            collector: Flow<TestItem>,
            onLoadingProgress: suspend (EtlLoadingResult) -> Unit,
            onStatusChanged: suspend (EtlStatus) -> Unit
        ): EtlLoadingResult {
            var lastItem: TestItem? = null
            var rowsProcessed = 0L
            collector.collect { item ->
                loadedItems.add(item)
                lastItem = item
                rowsProcessed++
            }
            return EtlLoadingResult(
                lastProcessedAt = lastItem?.timestamp ?: sinceTimestamp,
                processedRows = rowsProcessed
            ).also {
                onLoadingProgress(it)
                onStatusChanged(EtlStatus.SUCCESS)
            }
        }

        override suspend fun deleteAll(context: EtlContext) {
            loadedItems.clear()
        }
    }

    private class TransformedCapturingLoader : DataLoader<TransformedItem> {
        override val name = "transformed-capturing-loader"
        val loadedItems = mutableListOf<TransformedItem>()

        override suspend fun load(
            context: EtlContext,
            sinceTimestamp: Instant,
            untilTimestamp: Instant,
            collector: Flow<TransformedItem>,
            onLoadingProgress: suspend (EtlLoadingResult) -> Unit,
            onStatusChanged: suspend (EtlStatus) -> Unit
        ): EtlLoadingResult {
            var lastItem: TransformedItem? = null
            var rowsProcessed = 0L
            collector.collect { item ->
                loadedItems.add(item)
                lastItem = item
                rowsProcessed++
            }
            return EtlLoadingResult(
                lastProcessedAt = lastItem?.timestamp ?: sinceTimestamp,
                processedRows = rowsProcessed
            ).also {
                onLoadingProgress(it)
                onStatusChanged(EtlStatus.SUCCESS)
            }
        }

        override suspend fun deleteAll(context: EtlContext) {
            loadedItems.clear()
        }
    }

    private class ThrowingLoader : DataLoader<TestItem> {
        override val name = "throwing-loader"

        override suspend fun load(
            context: EtlContext,
            sinceTimestamp: Instant,
            untilTimestamp: Instant,
            collector: Flow<TestItem>,
            onLoadingProgress: suspend (EtlLoadingResult) -> Unit,
            onStatusChanged: suspend (EtlStatus) -> Unit
        ): EtlLoadingResult {
            collector.collect { throw RuntimeException("Simulated loader failure") }
            return EtlLoadingResult(lastProcessedAt = sinceTimestamp)
        }

        override suspend fun deleteAll(context: EtlContext) {}
    }

    private fun buildPipeline(loader: CapturingLoader) = EtlPipelineImpl(
        name = "test-pipeline",
        extractor = NoOpExtractor(),
        transformer = PassthroughTransformer(),
        loader = loader,
        metrics = EtlMeter(SimpleMeterRegistry())
    )

    private fun buildPipelineWithMappingTransformer(loader: TransformedCapturingLoader) = EtlPipelineImpl(
        name = "test-pipeline",
        extractor = NoOpExtractor(),
        transformer = PrefixingTransformer(),
        loader = loader,
        metrics = EtlMeter(SimpleMeterRegistry())
    )

    private fun buildPipelineWithThrowingLoader() = EtlPipelineImpl(
        name = "test-pipeline",
        extractor = NoOpExtractor(),
        transformer = PassthroughTransformer(),
        loader = ThrowingLoader(),
        metrics = EtlMeter(SimpleMeterRegistry())
    )
}

