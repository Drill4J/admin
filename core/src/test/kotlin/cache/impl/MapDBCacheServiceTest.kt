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
import com.epam.drill.admin.common.serialization.*
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.*
import kotlin.test.*

class MapDBCacheServiceTest {

    private val cacheService = MapDBCacheService()

    @AfterTest
    fun before() {
        serStore.clear()
    }

    @Serializable
    data class Build(
        val parentVersion: String = "",
        val total: Int = 0,
    )

    @Test
    fun `getOrCreate with string value`() {
        val cacheId = "test2codeAgentId"
        val cache: Cache<String, String> = cacheService.getOrCreate(cacheId, "build1")
        val key = "dest/example"
        val data = "2"
        cache[key] = data
        assertEquals(data, cache[key])
        assertNotEquals(NullCache.castUnchecked(), cache)
    }

    @Test
    fun `getOrCreate with kotlin object`() {
        val cacheId = "test2codeAgentId"
        val cache: Cache<String, Build> = cacheService.getOrCreate(cacheId, "build1")
        val key = "dest/build"
        val data = Build("asd", 2)
        cache[key] = data
        assertEquals(Build("asd", 2), cache[key])
        assertNotEquals(NullCache.castUnchecked(), cache)
    }

    @Test
    fun `getOrCreate twice`() {
        val cacheId = "test2codeAgentId"
        val cache: Cache<String, Build> = cacheService.getOrCreate(cacheId, "build1")
        val key = "dest/build"
        val data = Build("asd", 2)
        cache[key] = Build("asd", 2)
        assertEquals(data, cache[key])

        val sameCache: Cache<String, Build> = cacheService.getOrCreate(cacheId, "build1")
        assertEquals(data, sameCache[key])
    }

    @Test
    fun `getOrCreate with two kotlin object`() {
        val cacheId = "test2codeAgentId"
        val cache: Cache<String, Any> = cacheService.getOrCreate(cacheId, "build1")
        val key = "dest/build"
        val data = Build("asd", 2)
        cache["dest/data"] = "data"
        cache[key] = data
        assertEquals(Build("asd", 2), cache[key])
        assertNotEquals(NullCache.castUnchecked(), cache)
    }

    @Test
    fun `getOrCreate list object kotlin`() {
        val cacheId = "test2codeAgentId"
        val cache: Cache<String, List<Build>> = cacheService.getOrCreate(cacheId, "build1")
        val key = "dest/build"
        val data = Build("asd", 2)
        cache[key] = listOf(data, data)
        assertEquals(listOf(Build("asd", 2), Build("asd", 2)), cache[key])
        assertNotEquals(NullCache.castUnchecked(), cache)
    }

    @Test
    fun `getOrCreate different qualifiers`() {
        val cacheId = "test2codeAgentId"
        val cache: Cache<String, String> = cacheService.getOrCreate(cacheId, "build1")
        val key = "dest/example"
        val data = "2"
        cache[key] = data
        assertEquals(data, cache[key])
        assertNotEquals(NullCache.castUnchecked(), cache)
        //todo for what? remove?
//        val cache2: Cache<String, String> = cacheService.getOrCreate(cacheId, "build2")
//        assertEquals(NullCache.castUnchecked(), cache2)
//        assertEquals("", cache2.qualifier)
//        assertNull(cache2[key])
//        assertEquals(data, cache[key])
    }

    @Test //todo need it?
    fun `getOrCreate different qualifiers with replacing`() {
        val cacheId = "test2codeAgentId"
        val cache: Cache<String, String> = cacheService.getOrCreate(cacheId, "build1")
        val key = "dest/example"
        val data = "2"
        cache[key] = data
        assertEquals(data, cache[key])
        val cache2: Cache<String, String> = cacheService.getOrCreate(cacheId, "build2", true)
        assertNotEquals(NullCache.castUnchecked(), cache2)
        assertNotEquals(NullCache.castUnchecked(), cache)
        assertNotEquals(cache, cache2)
        assertEquals("build2", cache2.qualifier)
        assertNull(cache2[key])
        assertEquals(data, cache[key])
    }

}
