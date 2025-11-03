package com.epam.drill.admin.etl.iterator

import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
class BatchIteratorTest {

    private class TestBatchIterator(
        batchSize: Int,
        val totalData: List<Int>,
        val fetchCounter: AtomicInteger = AtomicInteger(0),
        initData: List<Int> = emptyList()
    ) : BatchIterator<Int>(batchSize, initData) {
        override fun fetchBatch(offset: Int, batchSize: Int): List<Int> {
            fetchCounter.incrementAndGet()
            return totalData.drop(offset).take(batchSize)
        }
    }

    @Test
    fun `should iterate through single batch`() {
        val data = listOf(1, 2, 3)
        val iterator = TestBatchIterator(batchSize = 10, totalData = data)

        assertTrue(iterator.hasNext())
        assertEquals(1, iterator.next())
        assertTrue(iterator.hasNext())
        assertEquals(2, iterator.next())
        assertTrue(iterator.hasNext())
        assertEquals(3, iterator.next())
        assertFalse(iterator.hasNext())
    }

    @Test
    fun `should iterate through multiple batches`() {
        val data = (1..25).toList()
        val iterator = TestBatchIterator(batchSize = 10, totalData = data)

        val result = mutableListOf<Int>()
        while (iterator.hasNext()) {
            result.add(iterator.next())
        }

        assertEquals(data, result)
        assertEquals(3, iterator.fetchCounter.get())
    }

    @Test
    fun `should handle empty data`() {
        val iterator = TestBatchIterator(batchSize = 10, totalData = emptyList())

        assertFalse(iterator.hasNext())
    }

    @Test
    fun `should throw exception when calling next on exhausted iterator`() {
        val iterator = TestBatchIterator(batchSize = 10, totalData = listOf(1, 2))

        iterator.next()
        iterator.next()

        assertFailsWith<NoSuchElementException> {
            iterator.next()
        }
    }

    @Test
    fun `should call hasNext multiple times without side effects`() {
        val data = listOf(1, 2, 3)
        val iterator = TestBatchIterator(batchSize = 10, totalData = data)

        assertTrue(iterator.hasNext())
        assertTrue(iterator.hasNext())
        assertTrue(iterator.hasNext())
        assertEquals(1, iterator.next())
        assertEquals(1, iterator.fetchCounter.get())
    }

    @Test
    fun `should initialize with init data`() {
        val initData = listOf(1, 2, 3)
        val remainingData = listOf(4, 5, 6)
        val allData = initData + remainingData
        val iterator = TestBatchIterator(batchSize = 3, totalData = allData, initData = initData)

        assertEquals(1, iterator.next())
        assertEquals(2, iterator.next())
        assertEquals(3, iterator.next())
        assertEquals(0, iterator.fetchCounter.get())

        assertEquals(4, iterator.next())
        assertEquals(5, iterator.next())
        assertEquals(6, iterator.next())
        assertEquals(1, iterator.fetchCounter.get())
        assertFalse(iterator.hasNext())
    }

    @Test
    fun `should handle init data smaller than batch size`() {
        val initData = listOf(1, 2)
        val iterator = TestBatchIterator(batchSize = 10, totalData = initData, initData = initData)

        assertEquals(1, iterator.next())
        assertEquals(2, iterator.next())
        assertEquals(0, iterator.fetchCounter.get())
        assertFalse(iterator.hasNext())
    }

}