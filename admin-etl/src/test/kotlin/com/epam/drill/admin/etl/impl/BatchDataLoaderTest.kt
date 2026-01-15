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

import com.epam.drill.admin.etl.EtlLoadingResult
import com.epam.drill.admin.etl.EtlStatus
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class BatchDataLoaderTest {

    private class TestItem(val timestamp: Instant, val data: String, val processable: Boolean = true)

    private open class TestBatchDataLoader(
        batchSize: Int,
        vacuumAfterRows: Int,
        private val failOnBatch: Int = -1,
        private val failOnTimestamp: Boolean = false,
        private val outOfOrder: Boolean = false
    ) : BatchDataLoader<TestItem>("test-loader", batchSize, vacuumAfterRows) {

        val loadedBatches = mutableListOf<List<TestItem>>()

        override fun getLastExtractedTimestamp(args: TestItem): Instant? {
            if (failOnTimestamp) return null
            if (outOfOrder && loadedBatches.isNotEmpty()) return args.timestamp.minusSeconds(1)
            return args.timestamp
        }

        override fun isProcessable(args: TestItem): Boolean = args.processable

        override suspend fun loadBatch(groupId: String, batch: List<TestItem>, batchNo: Int): BatchResult {
            if (failOnBatch == batchNo) {
                return BatchResult(success = false, rowsLoaded = 0, errorMessage = "Batch $batchNo failed")
            }
            loadedBatches.add(ArrayList(batch))
            return BatchResult(success = true, rowsLoaded = batch.size.toLong())
        }

        override suspend fun vacuum(groupId: String) {
        }

        override suspend fun deleteAll(groupId: String) {
        }
    }

    @Test
    fun `load should handle empty flow`() = runBlocking {
        val loader = TestBatchDataLoader(batchSize = 10, vacuumAfterRows = 20)
        val result = loader.load("test-group", Instant.EPOCH, Instant.now(), flowOf()) { }
        assertEquals(EtlStatus.SUCCESS, result.status)
        assertEquals(0, result.processedRows)
        assertEquals(0, loader.loadedBatches.size)
    }

    @Test
    fun `load should process single batch for flow smaller than batch size`() = runBlocking {
        val items = (1..5).map { TestItem(Instant.ofEpochSecond(it.toLong()), "item$it") }
        val loader = TestBatchDataLoader(batchSize = 10, vacuumAfterRows = 20)
        val result = loader.load("test-group", Instant.EPOCH, Instant.now(), flowOf(*items.toTypedArray())) { }

        assertEquals(EtlStatus.SUCCESS, result.status)
        assertEquals(5, result.processedRows)
        assertEquals(1, loader.loadedBatches.size)
        assertEquals(5, loader.loadedBatches[0].size)
    }

    @Test
    fun `load should process multiple batches`() = runBlocking {
        val items = (1..25).map { TestItem(Instant.ofEpochSecond(it.toLong()), "item$it") }
        val loader = TestBatchDataLoader(batchSize = 10, vacuumAfterRows = 20)
        val results = mutableListOf<EtlLoadingResult>()
        val result = loader.load("test-group", Instant.EPOCH, Instant.now(), flowOf(*items.toTypedArray())) { results.add(it) }

        assertEquals(EtlStatus.SUCCESS, result.status)
        assertEquals(25, result.processedRows)
        assertEquals(3, loader.loadedBatches.size)
        assertEquals(10, loader.loadedBatches[0].size)
        assertEquals(10, loader.loadedBatches[1].size)
        assertEquals(5, loader.loadedBatches[2].size)

        assertEquals(3, results.size)
        assertEquals(EtlStatus.LOADING, results[0].status)
        assertEquals(EtlStatus.LOADING, results[1].status)
        assertEquals(EtlStatus.SUCCESS, results[2].status)
    }

    @Test
    fun `load should skip non-processable items`() = runBlocking {
        val items = listOf(
            TestItem(Instant.ofEpochSecond(1), "item1"),
            TestItem(Instant.ofEpochSecond(2), "item2", processable = false),
            TestItem(Instant.ofEpochSecond(3), "item3")
        )
        val loader = TestBatchDataLoader(batchSize = 10, vacuumAfterRows = 20)
        val result = loader.load("test-group", Instant.EPOCH, Instant.now(), flowOf(*items.toTypedArray())) { }

        assertEquals(EtlStatus.SUCCESS, result.status)
        assertEquals(2, result.processedRows)
        assertEquals(1, loader.loadedBatches.size)
        assertEquals(2, loader.loadedBatches[0].size)
        assertEquals("item1", loader.loadedBatches[0][0].data)
        assertEquals("item3", loader.loadedBatches[0][1].data)
    }

    @Test
    fun `load should fail on batch processing error`() = runBlocking {
        val items = (1..15).map { TestItem(Instant.ofEpochSecond(it.toLong()), "item$it") }
        val loader = TestBatchDataLoader(batchSize = 10, vacuumAfterRows = 20, failOnBatch = 2)
        val result = loader.load("test-group", Instant.EPOCH, Instant.now(), flowOf(*items.toTypedArray())) { }

        assertEquals(EtlStatus.FAILED, result.status)
        assertEquals(10, result.processedRows) // First batch succeeds
        assertEquals(1, loader.loadedBatches.size)
        assertEquals(loader.loadedBatches[0].last().timestamp, result.lastProcessedAt)
    }

    @Test
    fun `load should fail on out-of-order timestamps`() = runBlocking {
        val items = listOf(
            TestItem(Instant.ofEpochSecond(2), "item2"),
            TestItem(Instant.ofEpochSecond(1), "item1")
        )
        val loader = TestBatchDataLoader(batchSize = 10, vacuumAfterRows = 20)
        val result = loader.load("test-group", Instant.EPOCH, Instant.now(), flowOf(*items.toTypedArray())) { }

        assertEquals(EtlStatus.FAILED, result.status)
    }

    @Test
    fun `load should skip already processed rows`() = runBlocking {
        val since = Instant.ofEpochSecond(5)
        val items = (1..10).map { TestItem(Instant.ofEpochSecond(it.toLong()), "item$it") }
        val loader = TestBatchDataLoader(batchSize = 10, vacuumAfterRows = 20)
        val result = loader.load("test-group", since, Instant.now(), flowOf(*items.toTypedArray())) { }

        assertEquals(EtlStatus.SUCCESS, result.status)
        assertEquals(5, result.processedRows)
        assertEquals(1, loader.loadedBatches.size)
        assertEquals(5, loader.loadedBatches[0].size)
        assertEquals("item6", loader.loadedBatches[0][0].data)
    }

    @Test
    fun `load should trigger vacuum after vacuumAfterRows`() = runBlocking {
        val items = (1..45).map { TestItem(Instant.ofEpochSecond(it.toLong()), "item$it") }
        val vacuumAfterRows = 20

        val loader = object : TestBatchDataLoader(batchSize = 10, vacuumAfterRows = vacuumAfterRows) {
            val vacuumCalls = mutableListOf<String>()

            override suspend fun vacuum(groupId: String) {
                vacuumCalls.add(groupId)
            }
        }

        loader.load("test-group", Instant.EPOCH, Instant.now(), flowOf(*items.toTypedArray())) { }

        assertEquals(2, loader.vacuumCalls.size)
        assertEquals(listOf("test-group", "test-group"), loader.vacuumCalls)
    }

}

