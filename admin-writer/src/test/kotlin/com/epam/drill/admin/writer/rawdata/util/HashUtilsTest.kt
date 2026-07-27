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
package com.epam.drill.admin.writer.rawdata.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HashUtilsTest {

    @Test
    fun `md5 must return lowercase hex digest`() {
        val hash = "hello".md5()
        assertEquals("5d41402abc4b2a76b9719d911017c592", hash)
        assertTrue(hash.matches(Regex("[0-9a-f]{32}")))
    }

    @Test
    fun `combineChecksumsCrc64 must be commutative (order-insensitive)`() {
        val checksums = listOf("-o40ap3ip2wwz", "-1v8ej17o75o3x", "16iwg6p4f3g2n", "-19xhogwpd8a9h")
        val hash1 = combineChecksumsCrc64(checksums)
        val hash2 = combineChecksumsCrc64(checksums.reversed())
        val hash3 = combineChecksumsCrc64(checksums.shuffled())
        assertEquals(hash1, hash2)
        assertEquals(hash1, hash3)
    }

    @Test
    fun `combineChecksumsCrc64 must account for duplicate checksums`() {
        val hashWithDuplicates = combineChecksumsCrc64(listOf("123", "123"))
        val hashSingle = combineChecksumsCrc64(listOf("123"))
        assertEquals((hashSingle.toLong(36) * 2).toString(36), hashWithDuplicates)
    }

    @Test
    fun `combineChecksumsCrc64 must return zero for empty input`() {
        assertEquals("0", combineChecksumsCrc64(emptyList()))
    }

    @Test
    fun `combineChecksumsCrc64 must wrap around on overflow (mod 2^64)`() {
        val hash = combineChecksumsCrc64(listOf(Long.MAX_VALUE.toString(36), "1"))
        assertEquals(Long.MIN_VALUE.toString(36), hash)
    }

    @Test
    fun `combineChecksumsCrc64 must throw for non-base36 checksum`() {
        assertFailsWith<InvalidChecksumException> {
            combineChecksumsCrc64(listOf("123", "not-a-number!"))
        }
    }
}
