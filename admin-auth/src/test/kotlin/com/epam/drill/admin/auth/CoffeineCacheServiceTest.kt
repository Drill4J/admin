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

    @Test
    fun `given principle with value null, then cache must not populate`(): Unit = runBlocking {
        cacheService.getFromCacheOrPutIfAbsent("custom-key-1") { null }

        Mockito.verify(caffeineMock, times(0)).put(any(), any())
    }
}