package com.epam.drill.admin.auth

import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.principal.User
import com.epam.drill.admin.auth.service.impl.CoffeineCacheService
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.runBlocking
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for [CoffeineCacheService] logic
 */
class CoffeineCacheServiceTest {

    @Test
    fun `given two distinct apiKeys, then cache size should be two`(): Unit = runBlocking {
        val cacheService = CoffeineCacheService(
            Caffeine.newBuilder()
                .maximumSize(5)
                .expireAfterWrite(Duration.ofMinutes(1))
                .build()
        )

        cacheService.getFromCacheOrPutIfAbsent("custom-key-1") { User(1, "test1", Role.USER) }
        cacheService.getFromCacheOrPutIfAbsent("custom-key-2") { User(2, "test2", Role.USER) }

        assertEquals(2, cacheService.cache.estimatedSize())
    }

    @Test
    fun `given only one apiKey two times, then cache size should be one`(): Unit = runBlocking {
        val user = User(1, "test1", Role.USER)
        val cacheService = CoffeineCacheService(
            Caffeine.newBuilder()
                .maximumSize(5)
                .expireAfterWrite(Duration.ofMinutes(1))
                .build()
        )

        cacheService.getFromCacheOrPutIfAbsent("custom-key-1") { user }
        cacheService.getFromCacheOrPutIfAbsent("custom-key-1") { user }

        assertEquals(1, cacheService.cache.estimatedSize())
    }

}