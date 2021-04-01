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
package com.epam.drill.admin.cache.impl

import com.epam.drill.admin.cache.type.*
import kotlin.test.*

class JvmCacheServiceTest {

    private val cacheService = JvmCacheService()

    @Test
    fun `getOrCreate different qualifiers`() {
        val cacheId = "test2codeAgentId"
        val cache: Cache<String, String> = cacheService.getOrCreate(cacheId, "build1")
        val key = "dest/example"
        val data = "2"
        cache[key] = data
        assertEquals(data, cache[key])
        assertNotEquals(NullCache.castUnchecked(), cache)
        val cache2: Cache<String, String> = cacheService.getOrCreate(cacheId, "build2")
        assertEquals(NullCache.castUnchecked(), cache2)
        assertEquals("", cache2.qualifier)
        assertNull(cache2[key])
        assertEquals(data, cache[key])
    }

    @Test
    fun `getOrCreate different qualifiers with replacing`() {
        val cacheId = "test2codeAgentId"
        val cache: Cache<String, String> = cacheService.getOrCreate(cacheId, "build1")
        val key = "dest/example"
        val data = "2"
        cache[key] = data
        assertEquals(data, cache[key])
        val cache2: Cache<String, String> = cacheService.getOrCreate(cacheId, "build2", true)
        val actual = cacheService.getOrCreate<Any, Any>(cacheId, "build1")
        assertEquals(NullCache.castUnchecked(), actual)
        assertNotEquals(NullCache.castUnchecked(), cache2)
        assertNotEquals(cache, cache2)
        assertEquals("build2", cache2.qualifier)
        assertNull(cache2[key])
        assertEquals(null, actual[key])
    }
}
