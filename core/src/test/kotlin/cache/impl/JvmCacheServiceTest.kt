package com.epam.drill.admin.cache.impl

import com.epam.drill.admin.cache.type.*
import kotlin.test.*

class JvmCacheServiceTest {

    private val cacheService = JvmCacheService()

    @Test
    fun `getOrCreate different qualifiers`() {
        val cache: Cache<String, String> = cacheService.getOrCreate("1", "1")
        cache["1"] = "2"
        assertEquals("2", cache["1"])
        assertNotEquals(NullCache.castUnchecked(), cache)
        val cache2: Cache<String, String> = cacheService.getOrCreate("1", "2")
        assertEquals(NullCache.castUnchecked(), cache2)
        assertEquals("", cache2.qualifier)
        assertNull(cache2["1"])
    }

    @Test
    fun `getOrCreate different qualifiers with replacing`() {
        val cache: Cache<String, String> = cacheService.getOrCreate("1", "1")
        cache["1"] = "2"
        assertEquals("2", cache["1"])
        val cache2: Cache<String, String> = cacheService.getOrCreate("1", "2", true)
        assertNotEquals(NullCache.castUnchecked(), cache2)
        assertNotEquals(cache, cache2)
        assertEquals("2", cache2.qualifier)
        assertNull(cache2["1"])
    }
}
