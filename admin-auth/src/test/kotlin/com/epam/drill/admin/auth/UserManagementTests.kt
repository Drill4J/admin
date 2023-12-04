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

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.epam.drill.admin.auth.config.CLAIM_ROLE
import com.epam.drill.admin.auth.config.CLAIM_USER_ID
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.entity.UserEntity
import com.epam.drill.admin.auth.repository.UserRepository
import com.epam.drill.admin.auth.service.PasswordService
import com.epam.drill.admin.auth.service.UserManagementService
import com.epam.drill.admin.auth.service.impl.UserManagementServiceImpl
import com.epam.drill.admin.auth.model.EditUserPayload
import com.epam.drill.admin.auth.model.UserView
import com.epam.drill.admin.auth.principal.User
import com.epam.drill.admin.auth.route.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.testing.*
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.kodein.di.bind
import org.kodein.di.eagerSingleton
import org.kodein.di.instance
import org.kodein.di.ktor.di
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.*

val USER_ADMIN
    get() = UserEntity(id = 1, username = "admin", passwordHash = "hash1", role = "ADMIN")
val USER_USER
    get() = UserEntity(id = 2, username = "user", passwordHash = "hash2", role = "USER")

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

        withTestApplication(withRoute { getUsersRoute() }) {
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
        val testRegistrationDate = LocalDateTime.of(2023, 1, 10, 12, 0, 0)
        wheneverBlocking(userRepository) { findById(1) }.thenReturn(
            UserEntity(
                id = 1, username = "admin", passwordHash = "hash", role = Role.ADMIN.name,
                registrationDate = testRegistrationDate
            )
        )

        withTestApplication(withRoute { getUserRoute() }) {
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
            .thenReturn(UserEntity(id = 1, username = "admin", passwordHash = "hash1", role = Role.ADMIN.name))
        wheneverBlocking(userRepository) { update(any()) }
            .thenAnswer(CopyUser)

        withTestApplication(withRoute { editUserRoute() }) {
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

        withTestApplication(withRoute { deleteUserRoute() }) {
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

        withTestApplication(withRoute { blockUserRoute() }) {
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

        withTestApplication(withRoute { unblockUserRoute() }) {
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

        withTestApplication(withRoute { resetPasswordRoute() }) {
            with(handleRequest(HttpMethod.Patch, "/users/1/reset-password") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
                verifyBlocking(userRepository) { update(USER_ADMIN.copy(passwordHash = "newhash")) }
            }
        }
    }

    @Test
    fun `given user identifier equal to current user, 'DELETE users {id}' must fail`() {
        withTestApplication(withRoute {
            authenticate {
                deleteUserRoute()
            }
        }) {
            with(handleRequest(HttpMethod.Delete, "/users/123") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                addJwtToken("foo", userId = 123)
            }) {
                assertEquals(HttpStatusCode.UnprocessableEntity, response.status())
            }
        }
    }

    @Test
    fun `given user identifier equal to current user, 'PATCH users {id} block' must fail`() {
        withTestApplication(withRoute {
            authenticate {
                blockUserRoute()
            }
        }) {
            with(handleRequest(HttpMethod.Patch, "/users/123/block") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                addJwtToken("foo", userId = 123)
            }) {
                assertEquals(HttpStatusCode.UnprocessableEntity, response.status())
            }
        }
    }

    @Test
    fun `given user identifier equal to current user, 'PUT users {id}' must fail`() {
        withTestApplication(withRoute {
            authenticate {
                editUserRoute()
            }
        }) {
            with(handleRequest(HttpMethod.Put, "/users/123") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                val form = EditUserPayload(role = Role.USER)
                setBody(Json.encodeToString(EditUserPayload.serializer(), form))
                addJwtToken("foo", userId = 123)
            }) {
                assertEquals(HttpStatusCode.UnprocessableEntity, response.status())
            }
        }
    }

    @Test
    fun `given external user, 'PATCH users {id} reset-password' must fail`() {
        wheneverBlocking(userRepository) { findById(123) }
            .thenReturn(
                //null password hash means the user is external
                UserEntity(id = 123, username = "external-user", passwordHash = null, role = "USER")
            )

        withTestApplication(withRoute {
            resetPasswordRoute()
        }) {
            with(handleRequest(HttpMethod.Patch, "/users/123/reset-password") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }) {
                assertEquals(HttpStatusCode.UnprocessableEntity, response.status())
            }
        }
    }

    @Test
    fun `given external user with external role management, 'PUT users {id}' must fail`() {
        wheneverBlocking(userRepository) { findById(123) }
            .thenReturn(
                //null password hash means the user is external
                UserEntity(id = 123, username = "external-user", passwordHash = null, role = "USER")
            )

        withTestApplication(moduleFunction = {
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
                        instance(),
                        externalRoleManagement = true
                    )
                }
            }
            install(StatusPages) {
                simpleAuthStatusPages()
            }
            routing {
                editUserRoute()
            }
        }) {
            with(handleRequest(HttpMethod.Put, "/users/123") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                val form = EditUserPayload(role = Role.ADMIN)
                setBody(Json.encodeToString(EditUserPayload.serializer(), form))
            }) {
                assertEquals(HttpStatusCode.UnprocessableEntity, response.status())
            }
        }
    }

    private fun withRoute(route: Routing.() -> Unit): Application.() -> Unit = {
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
        install(StatusPages) {
            simpleAuthStatusPages()
        }
        install(Authentication) {
            jwt {
                verifier(JWT.require(Algorithm.HMAC512(TEST_JWT_SECRET)).build())
                validate {
                    User(
                        id = it.payload.getClaim(CLAIM_USER_ID).asInt(),
                        username = it.payload.subject,
                        role = Role.valueOf(it.payload.getClaim(CLAIM_ROLE).asString())
                    )
                }
            }
        }
        routing {
            route()
        }
    }
}