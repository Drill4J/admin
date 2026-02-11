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

import com.epam.drill.admin.etl.EtlExtractingResult
import com.epam.drill.admin.etl.EtlRow
import com.epam.drill.admin.etl.impl.PageDataExtractorTest.TestPageDataExtractor.ExtractedPageInfo
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PageDataExtractorTest {

    private class TestItem(timestamp: Instant, val data: String) : EtlRow(timestamp)

    private class TestPageDataExtractor(
        extractionLimit: Int,
        private val data: List<TestItem> = emptyList(),
    ) : PageDataExtractor<TestItem>("test-extractor", extractionLimit) {

        val extractedPages = mutableListOf<ExtractedPageInfo>()

        data class ExtractedPageInfo(
            val groupId: String,
            val sinceTimestamp: Instant,
            val untilTimestamp: Instant,
            val limit: Int,
            val pageNumber: Int
        )

        override suspend fun extractPage(
            groupId: String,
            sinceTimestamp: Instant,
            untilTimestamp: Instant,
            limit: Int,
            onExtractionExecuted: suspend (duration: Long) -> Unit,
            rowsExtractor: suspend (row: TestItem) -> Unit
        ) {
            val info = ExtractedPageInfo(
                groupId = groupId,
                sinceTimestamp = sinceTimestamp,
                untilTimestamp = untilTimestamp,
                limit = limit,
                pageNumber = extractedPages.size + 1
            )
            extractedPages.add(info)
            onExtractionExecuted(100L)
            val page = data.filter { it.timestamp > sinceTimestamp && it.timestamp <= untilTimestamp }.take(limit)
            for (item in page) {
                rowsExtractor(item)
            }
        }
    }

    @Test
    fun `extract should handle empty data`() = runBlocking {
        val extractor = TestPageDataExtractor(
            extractionLimit = 10,
            data = emptyList()
        )

        val emittedItems = mutableListOf<TestItem>()
        val emitter = FlowCollector<TestItem> { emittedItems.add(it) }

        extractor.extract(
            groupId = "test-group",
            sinceTimestamp = Instant.EPOCH,
            untilTimestamp = Instant.ofEpochSecond(100),
            emitter = emitter,
            onExtractingProgress = {}
        )

        assertEquals(0, emittedItems.size)
        assertEquals(1, extractor.extractedPages.size)
    }

    @Test
    fun `extract should process single page when rows less than limit`() = runBlocking {
        val items = (1..5).map { TestItem(Instant.ofEpochSecond(it.toLong()), "item$it") }
        val extractor = TestPageDataExtractor(
            extractionLimit = 10,
            data = items
        )

        val emittedItems = mutableListOf<TestItem>()
        val emitter = FlowCollector<TestItem> { emittedItems.add(it) }

        extractor.extract(
            groupId = "test-group",
            sinceTimestamp = Instant.EPOCH,
            untilTimestamp = Instant.ofEpochSecond(100),
            emitter = emitter,
            onExtractingProgress = {}
        )

        assertEquals(5, emittedItems.size)
        assertEquals(1, extractor.extractedPages.size)
        assertEquals("item1", emittedItems[0].data)
        assertEquals("item5", emittedItems[4].data)
    }

    @Test
    fun `extract should process multiple pages`() = runBlocking {
        // First page: 10 items (limit reached)
        val page1 = (1..10).map { TestItem(Instant.ofEpochSecond(it.toLong()), "item$it") }
        // Second page: 10 items (limit reached)
        val page2 = (11..20).map { TestItem(Instant.ofEpochSecond(it.toLong()), "item$it") }
        // Third page: 5 items (less than limit, ends extraction)
        val page3 = (21..25).map { TestItem(Instant.ofEpochSecond(it.toLong()), "item$it") }

        val extractor = TestPageDataExtractor(
            extractionLimit = 10,
            data = listOf(page1, page2, page3).flatten()
        )

        val emittedItems = mutableListOf<TestItem>()
        val emitter = FlowCollector<TestItem> { emittedItems.add(it) }

        extractor.extract(
            groupId = "test-group",
            sinceTimestamp = Instant.EPOCH,
            untilTimestamp = Instant.ofEpochSecond(100),
            emitter = emitter,
            onExtractingProgress = {}
        )

        assertEquals(25, emittedItems.size)
        assertEquals(3, extractor.extractedPages.size)
        assertEquals("item1", emittedItems[0].data)
        assertEquals("item25", emittedItems[24].data)
    }

    @Test
    fun `extract should buffer to avoid missing items with same timestamp at edges of pages`() = runBlocking {
        // Multiple rows with same timestamp should be buffered and emitted together
        val items = listOf(
            TestItem(Instant.ofEpochSecond(1), "item1a"),
            TestItem(Instant.ofEpochSecond(2), "item2a"),
            TestItem(Instant.ofEpochSecond(2), "item2b"),
            TestItem(Instant.ofEpochSecond(3), "item3a"),
            TestItem(Instant.ofEpochSecond(3), "item3b")
        )

        val extractor = TestPageDataExtractor(
            extractionLimit = 3,
            data = items
        )

        val emittedItems = mutableListOf<Pair<TestItem, ExtractedPageInfo>>()
        val emitter = FlowCollector<TestItem> {
            emittedItems.add(it to extractor.extractedPages.last())
        }

        extractor.extract(
            groupId = "test-group",
            sinceTimestamp = Instant.EPOCH,
            untilTimestamp = Instant.ofEpochSecond(100),
            emitter = emitter,
            onExtractingProgress = {}
        )

        assertEquals(5, emittedItems.size)
        // All items should be emitted in order
        assertEquals("item1a", emittedItems[0].first.data)
        assertEquals(1, emittedItems[0].second.pageNumber)
        assertEquals("item2a", emittedItems[1].first.data)
        assertEquals(2, emittedItems[1].second.pageNumber)
        assertEquals("item2b", emittedItems[2].first.data)
        assertEquals(2, emittedItems[2].second.pageNumber)
        assertEquals("item3a", emittedItems[3].first.data)
        assertEquals(3, emittedItems[3].second.pageNumber)
        assertEquals("item3b", emittedItems[4].first.data)
        assertEquals(3, emittedItems[4].second.pageNumber)
    }

    @Test
    fun `extract should fail when timestamps are out of order`() = runBlocking {
        val items = listOf(
            TestItem(Instant.ofEpochSecond(2), "item2"),
            TestItem(Instant.ofEpochSecond(1), "item1") // Out of order
        )

        val extractor = TestPageDataExtractor(
            extractionLimit = 10,
            data = items
        )

        val emittedItems = mutableListOf<TestItem>()
        val emitter = FlowCollector<TestItem> { emittedItems.add(it) }

        val progressResults = mutableListOf<EtlExtractingResult>()
        extractor.extract(
            groupId = "test-group",
            sinceTimestamp = Instant.EPOCH,
            untilTimestamp = Instant.ofEpochSecond(100),
            emitter = emitter,
            onExtractingProgress = { progressResults.add(it) }
        )

        // Should catch the error and report it via progress callback
        assertTrue(progressResults.any { it.errorMessage != null })
        assertTrue(progressResults.any {
            it.errorMessage?.contains("not in ascending order") == true
        })
    }

    @Test
    fun `extract should throw exception when all rows have same timestamp and page is full`() = runBlocking {
        // All rows with same timestamp, page is full (equals limit)
        val items = (1..10).map { TestItem(Instant.ofEpochSecond(1), "item$it") }

        val extractor = TestPageDataExtractor(
            extractionLimit = 10,
            data = items
        )

        val emittedItems = mutableListOf<TestItem>()
        val emitter = FlowCollector<TestItem> { emittedItems.add(it) }

        val progressResults = mutableListOf<EtlExtractingResult>()
        extractor.extract(
            groupId = "test-group",
            sinceTimestamp = Instant.EPOCH,
            untilTimestamp = Instant.ofEpochSecond(100),
            emitter = emitter,
            onExtractingProgress = { progressResults.add(it) }
        )

        // Should report error about needing to increase extraction limit
        assertTrue(progressResults.any {
            it.errorMessage?.contains("increase the extraction limit") == true
        })
    }
}
