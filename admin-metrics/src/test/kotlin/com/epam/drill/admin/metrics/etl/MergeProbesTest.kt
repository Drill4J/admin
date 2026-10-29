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
package com.epam.drill.admin.metrics.etl

import org.postgresql.util.PGobject
import kotlin.random.Random
import kotlin.system.measureTimeMillis
import kotlin.test.*

class MergeProbesTest {

    private fun pgobject(value: String) = PGobject().apply {
        type = "varbit"
        this.value = value
    }

    @Test
    fun `mergeProbes basic OR`() {
        val result = mergeProbes(pgobject("0101"), pgobject("1001"))
        assertEquals("1101", result.getValue())
        assertEquals("varbit", result.type)
    }

    @Test
    fun `mergeProbes all zeros`() {
        val result = mergeProbes(pgobject("0000"), pgobject("0000"))
        assertEquals("0000", result.getValue())
    }

    @Test
    fun `mergeProbes all ones`() {
        val result = mergeProbes(pgobject("1111"), pgobject("1111"))
        assertEquals("1111", result.getValue())
    }

    @Test
    fun `mergeProbes length mismatch throws`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            mergeProbes(pgobject("010"), pgobject("01"))
        }
        assertTrue("different lengths" in ex.message!!, "Message should mention 'different lengths': ${ex.message}")
    }

    @Test
    fun `mergeProbes null current throws`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            mergeProbes(null, pgobject("0"))
        }
        assertTrue("null" in ex.message!!, "Message should mention 'null': ${ex.message}")
    }

    @Test
    fun `mergeProbes null next throws`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            mergeProbes(pgobject("0"), null)
        }
        assertTrue("null" in ex.message!!, "Message should mention 'null': ${ex.message}")
    }

    @Test
    fun `mergeProbes non-PGobject throws`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            mergeProbes("01", pgobject("01"))
        }
        assertTrue("PGobject" in ex.message!!, "Message should mention 'PGobject': ${ex.message}")
    }
}
