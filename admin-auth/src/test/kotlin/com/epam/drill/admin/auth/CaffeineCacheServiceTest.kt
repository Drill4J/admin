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

import com.epam.drill.admin.common.principal.Role
import com.epam.drill.admin.common.principal.User
import com.epam.drill.admin.auth.service.impl.CaffeineCacheService
import com.github.benmanes.caffeine.cache.Cache
import io.ktor.server.auth.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.mockito.kotlin.*
import kotlin.test.*


/**
 * Tests for [CaffeineCacheService] logic
 */
class CaffeineCacheServiceTest {

    private val caffeineMock = mock<Cache<String, Principal>>()
    private var cacheService = CaffeineCacheService(caffeineMock)

    @AfterEach
    fun reset() {
        reset(caffeineMock)
    }

    @Test
    fun `if api key is not in cache, getFromCacheOrPutIfAbsent must return user from callback function and put it in cache`(): Unit =
        runBlocking {
            val key = "custom-key"
            val user = User(1, "test1", Role.USER)
            whenever(caffeineMock.getIfPresent(key)).thenReturn(null)

            val principal = cacheService.getFromCacheOrPutIfAbsent(key) { user }

            verify(caffeineMock).put(key, user)
            assertEquals(user, principal)
        }

    @Test
    fun `if api key is already in cache, getFromCacheOrPutIfAbsent function must return user from cache`(): Unit =
        runBlocking {
            val user = User(1, "test1", Role.USER)
            val key = "custom-key"
            whenever(caffeineMock.getIfPresent(key)).thenReturn(user)

            val principal = cacheService.getFromCacheOrPutIfAbsent(key) {
                fail(message = "Must not be called")
            }

            assertEquals(user, principal)
        }

    @Test
    fun `given null principle in callback function, getFromCacheOrPutIfAbsent must return null and cache must not populate`(): Unit =
        runBlocking {
            val principal = cacheService.getFromCacheOrPutIfAbsent("custom-key") { null }

            assertNull(principal)
            verify(caffeineMock, never()).put(any(), any())
        }
}
