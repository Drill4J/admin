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
package com.epam.drill.admin.metrics.views

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class PagedListTest {
    @Test
    fun `should return correct items and total when items less than pageSize`() = runBlocking {
        val items = listOf(1, 2, 3)
        val pagedList = pagedListOf(1, 5) { offset, limit -> items.drop(offset).take(limit) }
        assertEquals(items, pagedList.items)
        assertEquals(3, pagedList.items.size)
        assertEquals(3, pagedList.total)
    }

    @Test
    fun `should return correct items and total when items equal to pageSize`() = runBlocking {
        val items = (1..5).toList()
        val pagedList = pagedListOf(1, 5) { offset, limit -> items.drop(offset).take(limit) }
            .withTotal { 10L }
        assertEquals(items, pagedList.items)
        assertEquals(5, pagedList.items.size)
        assertEquals(10L, pagedList.total)
    }

    @Test
    fun `should return correct items for next pages`() = runBlocking {
        val items = (1..10).toList()
        val pagedList = pagedListOf(2, 3) { offset, limit -> items.drop(offset).take(limit) }
            .withTotal { 10L }
        assertEquals(listOf(4, 5, 6), pagedList.items)
        assertEquals(3, pagedList.items.size)
        assertEquals(10L, pagedList.total)
    }

    @Test
    fun `should return total as calculated when last page is not full`() = runBlocking {
        val items = (1..10).toList()
        var totalFuncCalled = false
        val pagedList = pagedListOf(4, 3) { offset, limit -> items.drop(offset).take(limit) }
            .withTotal {
                totalFuncCalled = true
                100L // Should not be used
            }
        assertEquals(listOf(10), pagedList.items)
        assertEquals(1, pagedList.items.size)
        assertEquals(10L, pagedList.total)
        assertTrue(!totalFuncCalled, "totalFunc should not be called when last page is not full")
    }
}
