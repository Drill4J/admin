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
package com.epam.drill.plugins.test2code

import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.coverage.*
import kotlin.test.*

class CoverageTest {
    @Test
    fun `count - operation minus`() {
        assertEquals(0L to 0L, zeroCount subtraction zeroCount)
        assertEquals(1L to 2L, Count(1, 2) subtraction  zeroCount)
        assertEquals(1L to 2L, Count(1, 2) subtraction Count(2, 0))
        assertEquals(0L to 3L, Count(1, 3) subtraction Count(1, 3))
        assertEquals(1L to 6L, Count(1, 2) subtraction Count(1, 3))
        assertEquals(2L to 6L, Count(4, 6) subtraction Count(1, 3))
        assertEquals(3L to 6L, Count(1, 2) subtraction Count(0, 3))
    }

    @Test
    fun `arrowType - zero to zero`() {
        assertEquals(ArrowType.UNCHANGED, zeroCount.arrowType(zeroCount))
    }

    @Test
    fun `arrowType zero to non-zero`() {
        assertEquals(ArrowType.UNCHANGED, zeroCount.arrowType(Count(1, 2)))
    }

    @Test
    fun `arrowType simple cases`() {
        val count1 = Count(1, 3)
        val count2 = Count(1, 2)
        assertEquals(ArrowType.INCREASE, count1.arrowType(count2))
        assertEquals(ArrowType.DECREASE, count2.arrowType(count1))
        assertEquals(ArrowType.UNCHANGED, count1.arrowType(count1))
    }

    @Test
    fun `arrowType same ratio`() {
        val count1 = Count(1, 2)
        val count2 = Count(2, 4)
        assertEquals(ArrowType.UNCHANGED, count1.arrowType(count2))
        assertEquals(ArrowType.UNCHANGED, count2.arrowType(count1))
    }
}
