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
import com.epam.drill.admin.auth.repository.impl.EnvUserRepository
import com.epam.drill.admin.auth.service.PasswordService
import io.ktor.config.*
import kotlinx.coroutines.runBlocking
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import kotlin.test.*

class EnvRepositoryTest {
    @Mock
    lateinit var passwordService: PasswordService

    @BeforeTest
    fun setup() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `given users from env config, findAll must return all users not deleted users`() = runBlocking {
        val repository = prepareEnvUserRepository(
            user("user", role = Role.USER),
            user("admin", role = Role.ADMIN)
        )

        val users = repository.findAll()

        assertEquals(2, users.size)
        assertTrue(users.any { it.username == "user" && it.passwordHash == "hash" && it.role == "USER" })
        assertTrue(users.any { it.username == "admin" && it.passwordHash == "hash" && it.role == "ADMIN" })
    }

    @Test
    fun `given username hash, findById must return the respective user`() = runBlocking {
        val repository = prepareEnvUserRepository(
            user("guest"),
            user("foobar"),
            user("admin")
        )

        val user = repository.findById("foobar".hashCode())

        assertNotNull(user)
        assertTrue(user.username == "foobar")
    }

    @Test
    fun `given lowercase username hash, findById must return the respective user`() = runBlocking {
        val repository = prepareEnvUserRepository(
            user("guest"),
            user("FooBar"),
            user("admin")
        )

        val user = repository.findById("foobar".hashCode())

        assertNotNull(user)
        assertTrue(user.username == "FooBar")
    }

    @Test
    fun `given username findByUsername must return the respective user`() = runBlocking {
        val repository = prepareEnvUserRepository(
            user("guest"),
            user("foobar"),
            user("foobar123")
        )

        val user = repository.findByUsername("foobar")

        assertNotNull(user)
        assertTrue(user.username == "foobar")
    }

    @Test
    fun `given case insensitive username, findByUsername must return user`() = runBlocking {
        val repository = prepareEnvUserRepository(
            user("guest"),
            user("fooBAR"),
            user("FooBar123")
        )

        val user = repository.findByUsername("FooBar")

        assertNotNull(user)
        assertTrue(user.username == "fooBAR")
    }

    private fun prepareEnvUserRepository(vararg users: String): EnvUserRepository {
        whenever(passwordService.hashPassword(any())).thenAnswer { "hash" }
        val repository = EnvUserRepository(
            MapApplicationConfig().apply {
                put("drill.auth.envUsers", users.toList())
            },
            passwordService
        )
        return repository
    }
}

private fun user(
    username: String,
    password: String = "secret",
    role: Role = Role.USER
) = "{\"username\": \"$username\", \"password\": \"$password\", \"role\": \"${role.name}\"}"