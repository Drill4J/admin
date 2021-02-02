/**
 * Copyright 2020 EPAM Systems
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
package com.epam.drill.admin.util

import kotlin.test.*

class BiSetMapTest {
    @Test
    fun `put key and value`() {
        val empty = emptyBiSetMap<String, Any>()
        val key = "key"
        val value = Any()
        val withData = empty.put(key, value)
        assertEquals(setOf(value), withData.first[key])
        assertEquals(setOf(key), withData.second[value])
    }

    @Test
    fun `remove value`() {
        val empty = emptyBiSetMap<String, Any>()
        val key = "key"
        val value = Any()
        val bsmap = empty.put(key, value)
        assertSame(empty, bsmap.remove(value))
        assertSame(bsmap, bsmap.remove(Any()))

    }

    @Test
    fun `remove key-value`() {
        val empty = emptyBiSetMap<String, Any>()
        val key = "key"
        val value = Any()
        val bsmap = empty.put(key, value)
        assertSame(empty, bsmap.remove(key, value))
        assertSame(bsmap, bsmap.remove(key, Any()))
        assertSame(bsmap, bsmap.remove("nokey", value))
    }
}
