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

import com.epam.drill.admin.auth.repository.impl.EnvUserRepository
import com.epam.drill.admin.auth.service.PasswordService
import io.ktor.config.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import kotlin.test.*

private const val USER1 = "{\"username\": \"user\", \"password\": \"secret1\", \"role\": \"USER\"}"
private const val USER2 = "{\"username\": \"admin\", \"password\": \"secret2\", \"role\": \"ADMIN\"}"

class EnvRepositoryTest {
    @Mock
    lateinit var passwordService: PasswordService

    @BeforeTest
    fun setup() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `given users from env config, findAllNotDeleted must return all users not deleted users`() {
        val repository = getRepositoryWithUsers(USER1, USER2)

        val users = repository.findAllNotDeleted()

        assertEquals(2, users.size)
        assertTrue(users.any { it.username == "user" && it.passwordHash == "hash" && it.role == "USER" })
        assertTrue(users.any { it.username == "admin" && it.passwordHash == "hash" && it.role == "ADMIN" })
    }

    @Test
    fun `given username hash, findById must return the respective user`() {
        val repository = getRepositoryWithUsers(USER1, USER2)
        val user = repository.findById("user".hashCode())
        assertNotNull(user)
        assertTrue(user.username == "user")
    }

    @Test
    fun `given username findByUsername must return the respective user`() {
        val repository = getRepositoryWithUsers(USER1, USER2)
        val user = repository.findByUsername("user")
        assertNotNull(user)
        assertTrue(user.username == "user")
    }

    private fun getRepositoryWithUsers(vararg users: String): EnvUserRepository {
        whenever(passwordService.hashPassword(any())).thenAnswer { "hash" }
        val repository = EnvUserRepository(
            MapApplicationConfig().apply {
                put("drill.users", users.toList())
            },
            passwordService
        )
        return repository
    }
}