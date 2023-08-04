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
package com.epam.drill.plugins.test2code.util

import java.net.*
import kotlin.test.*

class UtilTest {
    @Test
    fun `urlDecode - empty input`() {
        val input = ""
        val encoded = URLEncoder.encode(input, Charsets.UTF_8.name())
        val decoded = encoded.urlDecode()
        assertEquals(input, decoded)
    }

    @Test
    fun `urlDecode - url compatible input`() {
        val input = "input"
        val encoded = URLEncoder.encode(input, Charsets.UTF_8.name())
        val decoded = encoded.urlDecode()
        assertSame(input, decoded)
    }

    @Test
    fun `urlDecode - incorrect input`() {
        val input = "%%%%%%something%"
        val decoded = input.urlDecode()
        assertSame(input, decoded)
    }

    @Test
    fun `urlDecode - cyrillic characters`() {
        val input = "пример названи�? те�?та 1"
        val encoded = URLEncoder.encode(input, Charsets.UTF_8.name())
        val decoded = encoded.urlDecode()
        assertEquals(input, decoded)
    }

    @Test
    fun `percentOf - cases with zero`() {
        assertEquals(0.0, 1 percentOf 0)
        assertEquals(0.0, 1 percentOf 0)
        assertEquals(0.0, 1L percentOf 0L)
        assertEquals(0.0, 1 percentOf 0.0f)
        assertEquals(0.0, 1.0 percentOf 0.0)
        assertEquals(0.0, 0 percentOf 10)
    }

    @Test
    fun `gcd - greatest common divisor`() {
        assertEquals(1, 1.gcd(1))
        assertEquals(3, 3.gcd(3))
    }
}
