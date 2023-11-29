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
import com.epam.drill.admin.auth.entity.UserEntity
import com.epam.drill.admin.auth.repository.UserRepository
import com.epam.drill.admin.auth.route.userManagementRoutes
import com.epam.drill.admin.auth.service.PasswordService
import com.epam.drill.admin.auth.service.UserManagementService
import com.epam.drill.admin.auth.service.impl.UserManagementServiceImpl
import com.epam.drill.admin.auth.model.EditUserPayload
import com.epam.drill.admin.auth.model.UserView
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.testing.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.kodein.di.bind
import org.kodein.di.eagerSingleton
import org.kodein.di.instance
import org.kodein.di.ktor.di
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import kotlin.test.*

val USER_ADMIN
    get() = UserEntity(id = 1, username = "admin", passwordHash = "hash1", role = "ADMIN").copy()
val USER_USER
    get() = UserEntity(id = 2, username = "user", passwordHash = "hash2", role = "USER").copy()

/**
 * Testing /users routers and UserManagementServiceImpl
 */
class UserManagementTest {

    @Mock
    lateinit var userRepository: UserRepository
    @Mock
    lateinit var passwordService: PasswordService

    @BeforeTest
    fun setup() {
        MockitoAnnotations.openMocks(this)
    }


    @Test
    fun `'GET users' must return the expected number of users from repository`() {
        wheneverBlocking(userRepository) { findAll() }
            .thenReturn(listOf(USER_ADMIN, USER_USER))

        withTestApplication(config) {
            with(handleRequest(HttpMethod.Get, "/users") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
                val response: List<UserView> = assertResponseNotNull(ListSerializer(UserView.serializer()))
                assertEquals(2, response.size)
            }
        }
    }

    @Test
    fun `given existing user identifier 'GET users {id}' must return the respective user`() {
        wheneverBlocking(userRepository) { findById(1) }
            .thenReturn(USER_ADMIN)

        withTestApplication(config) {
            with(handleRequest(HttpMethod.Get, "/users/1") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
                val response: UserView = assertResponseNotNull(UserView.serializer())
                assertEquals("admin", response.username)
            }
        }
    }

    @Test
    fun `given user identifier and role 'PUT users {id}' must change user role in repository and return changed user`() {
        wheneverBlocking(userRepository) { findById(1) }
            .thenReturn(USER_ADMIN)

        withTestApplication(config) {
            with(handleRequest(HttpMethod.Put, "/users/1") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                val form = EditUserPayload(role = Role.USER)
                setBody(Json.encodeToString(EditUserPayload.serializer(), form))
            }) {
                verifyBlocking(userRepository) { update(USER_ADMIN.copy(role = "USER")) }
                assertEquals(HttpStatusCode.OK, response.status())
                val response: UserView = assertResponseNotNull(UserView.serializer())
                assertEquals(Role.USER, response.role)
            }
        }
    }

    @Test
    fun `given user identifier 'DELETE users {id}' must delete that user in repository`() {
        wheneverBlocking(userRepository) { findById(1) }
            .thenReturn(USER_ADMIN)

        withTestApplication(config) {
            with(handleRequest(HttpMethod.Delete, "/users/1") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
                verifyBlocking(userRepository) { deleteById(1) }
            }
        }
    }

    @Test
    fun `given user identifier 'PATCH users {id} block' must block that user in repository`() {
        wheneverBlocking(userRepository) { findById(1) }
            .thenReturn(USER_ADMIN)

        withTestApplication(config) {
            with(handleRequest(HttpMethod.Patch, "/users/1/block") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
                verifyBlocking(userRepository) { update(USER_ADMIN.copy(blocked = true)) }
            }
        }
    }

    @Test
    fun `given user identifier 'PATCH users {id} unblock' must unblock that user in repository`() {
        wheneverBlocking(userRepository) { findById(1) }
            .thenReturn(USER_ADMIN.copy(blocked = true))

        withTestApplication(config) {
            with(handleRequest(HttpMethod.Patch, "/users/1/unblock") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
                verifyBlocking(userRepository) { update(USER_ADMIN.copy(blocked = false)) }
            }
        }
    }

    @Test
    fun `given user identifier 'PATCH users {id} reset-password' must generate and return a new password of that user`() {
        wheneverBlocking(userRepository) { findById(1) }
            .thenReturn(USER_ADMIN)
        whenever(passwordService.generatePassword())
            .thenReturn("newsecret")
        whenever(passwordService.hashPassword("newsecret"))
            .thenReturn("newhash")

        withTestApplication(config) {
            with(handleRequest(HttpMethod.Patch, "/users/1/reset-password") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
                verifyBlocking(userRepository) { update(USER_ADMIN.copy(passwordHash = "newhash")) }
            }
        }
    }

    private val config: Application.() -> Unit = {
        install(Locations)
        install(ContentNegotiation) {
            json()
        }
        di {
            bind<UserRepository>() with eagerSingleton { userRepository }
            bind<PasswordService>() with eagerSingleton { passwordService }
            bind<UserManagementService>() with eagerSingleton {
                UserManagementServiceImpl(
                    instance(),
                    instance()
                )
            }
        }
        routing {
            userManagementRoutes()
        }
    }
}