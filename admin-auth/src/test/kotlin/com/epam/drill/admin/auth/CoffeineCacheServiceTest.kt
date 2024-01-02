package com.epam.drill.admin.auth

import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.principal.User
import com.epam.drill.admin.auth.service.impl.CoffeineCacheService
import com.github.benmanes.caffeine.cache.Cache
import io.ktor.auth.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.fail


/**
 * Tests for [CoffeineCacheService] logic
 */
class CoffeineCacheServiceTest {

    private val caffeineMock = mock<Cache<String, Principal>>()
    private var cacheService = CoffeineCacheService(caffeineMock)

    @AfterEach
    fun reset() {
        Mockito.reset(caffeineMock)
    }

    @Test
    fun `given apiKey, then should be called put method`(): Unit = runBlocking {
        val key1 = "custom-key-1"
        val user1 = User(1, "test1", Role.USER)

        val principal = cacheService.getFromCacheOrPutIfAbsent(key1) { user1 }

        Mockito.verify(caffeineMock, times(1)).put(key1, user1)
        assertNotNull(principal)
    }

    @Test
    fun `if api key is already in cache, getFromCacheOrPutIfAbsent function must return user from cache`(): Unit =
        runBlocking {
            val user = User(1, "test1", Role.USER)
            val key = "custom-key-1"
            Mockito.`when`(caffeineMock.getIfPresent(key)).thenReturn(user)

            val principal = cacheService.getFromCacheOrPutIfAbsent(key) {
                fail(message = "Must not be called")
            }

            assertNotNull(principal)
        }

}