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
import kotlinx.serialization.*
import kotlin.test.*

class MapDBCacheServiceTest {

    private val cacheService = MapDBCacheService()

    @AfterTest
    fun before() {
        cacheService.serializers.clear()
    }

    @Serializable
    data class Build(
        val parentVersion: String = "",
        val total: Int = 0,
    )

    @Test
    fun `cache with string value should set and get`() {
        val cacheId = "test2codeAgentId"
        val cache: Cache<String, String> = cacheService.getOrCreate(cacheId, "build1")
        val key = "dest/example"
        val data = "2"
        cache[key] = data
        assertEquals(data, cache[key])
        assertNotEquals(NullCache.castUnchecked(), cache)
    }

    @Test
    fun `cache with kotlin object should set and get`() {
        val cacheId = "test2codeAgentId"
        val cache: Cache<String, Build> = cacheService.getOrCreate(cacheId, "build1")
        val key = "dest/build"
        val data = Build("asd", 2)
        cache[key] = data
        assertEquals(Build("asd", 2), cache[key])
        assertNotEquals(NullCache.castUnchecked(), cache)
    }

    @Test
    fun `cache with two kotlin object should set and get`() {
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
    fun `cache list object kotlin should set and get`() {
        val cacheId = "test2codeAgentId"
        val cache: Cache<String, List<Build>> = cacheService.getOrCreate(cacheId, "build1")
        val key = "dest/build"
        val data = Build("asd", 2)
        cache[key] = listOf(data, data)
        assertEquals(listOf(Build("asd", 2), Build("asd", 2)), cache[key])
        assertNotEquals(NullCache.castUnchecked(), cache)
    }

    @Test
    fun `cache empty list object kotlin should set and get`() {
        val cacheId = "test2codeAgentId"
        val cache: Cache<String, List<Build>> = cacheService.getOrCreate(cacheId, "build1")
        val key = "dest/build"
        val data = Build("asd", 2)
        cache[key] = emptyList()
        cache[key] = listOf(data, data)
        assertEquals(listOf(Build("asd", 2), Build("asd", 2)), cache[key])
        assertNotEquals(NullCache.castUnchecked(), cache)
    }

    @Test
    fun `twice invoke getOrCreate it should same cache value`() {
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
    fun `cache set empty string should deserialize`() {
        val cacheId = "test2codeAgentId"
        val cache: Cache<Any, Any> = cacheService.getOrCreate(cacheId, "build1")
        val key = "dest/build"
        val data = ""
        cache[key] = Build("asd", 2)
        assertEquals(Build("asd", 2), cache[key])

        cache[key] = ""
        assertEquals(data, cache[key])
    }

    @Test
    fun `cache for one key set empty string, object should deserialize`() {
        val cacheId = "test2codeAgentId"
        val cache: Cache<Any, Any> = cacheService.getOrCreate(cacheId, "build1")
        val key = "dest/build"
        val data = ""

        cache[key] = ""
        assertEquals(data, cache[key])

        cache[key] = Build("asd", 2)
        assertEquals(Build("asd", 2), cache[key])
    }

}
