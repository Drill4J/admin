package com.epam.drill.admin.etl.iterator

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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
class FanOutSequenceTest {

    @Test
    fun `should fan out items between multiple iterators`() {
        val source = (1..5).iterator()
        val shared = FanOutSequence(source)

        val iter1 = shared.iterator()
        val iter2 = shared.iterator()

        // First iterator consumes first 3 elements
        assertEquals(1, iter1.next())
        assertEquals(2, iter1.next())
        assertEquals(3, iter1.next())

        // Second iterator starts from the beginning
        assertEquals(1, iter2.next())
        assertEquals(2, iter2.next())

        // First iterator continues its consumption
        assertEquals(4, iter1.next())

        // Second iterator continues its consumption
        assertEquals(3, iter2.next())

        // First iterator ends consumption
        assertEquals(5, iter1.next())
        assertFalse(iter1.hasNext())

        // Second iterator continues
        assertEquals(4, iter2.next())
        assertEquals(5, iter2.next())
        assertFalse(iter2.hasNext())
    }

    @Test
    fun `should start new iterator from last available element when created after others started consuming`() {
        val source = (1..5).iterator()
        val shared = FanOutSequence(source)

        val iter1 = shared.iterator()

        // First iterator consumes some elements
        assertEquals(1, iter1.next())
        assertEquals(2, iter1.next())
        assertEquals(3, iter1.next())

        // Create second iterator after first one has already consumed elements
        val iter2 = shared.iterator()

        // Second iterator should start from element 4, not from 1
        assertEquals(4, iter2.next())
        assertEquals(5, iter2.next())

        // First iterator continues normally
        assertEquals(4, iter1.next())
        assertEquals(5, iter1.next())
    }


}