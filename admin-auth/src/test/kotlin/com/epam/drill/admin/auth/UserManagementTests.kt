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
import com.epam.drill.admin.auth.entity.UserEntity
import com.epam.drill.admin.auth.repository.UserRepository
import com.epam.drill.admin.auth.service.PasswordService
import com.epam.drill.admin.auth.service.UserManagementService
import com.epam.drill.admin.auth.service.impl.UserManagementServiceImpl
import com.epam.drill.admin.auth.model.EditUserPayload
import com.epam.drill.admin.auth.model.UserView
import com.epam.drill.admin.auth.route.*
import com.epam.drill.admin.auth.service.PasswordGenerator
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.resources.*
import io.ktor.server.testing.*
import io.ktor.client.request.*
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
    @Mock
    lateinit var passwordGenerator: PasswordGenerator

    @BeforeTest
    fun setup() {
        MockitoAnnotations.openMocks(this)
    }


    @Test
    fun `'GET users' must return the expected number of users from repository`() {
        wheneverBlocking(userRepository) { findAll() }
            .thenReturn(listOf(USER_ADMIN, USER_USER))

        testApplication {
            application(withRoute { getUsersRoute() })
            val response = client.get("/users") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }
            assertEquals(HttpStatusCode.OK, response.status)
            val body: List<UserView> = response.assertResponseNotNull(ListSerializer(UserView.serializer()))
            assertEquals(2, body.size)
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

        testApplication {
            application(withRoute { getUserRoute() })
            val response = client.get("/users/1") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }
            assertEquals(HttpStatusCode.OK, response.status)
            val body: UserView = response.assertResponseNotNull(UserView.serializer())
            assertEquals("admin", body.username)
        }
    }

    @Test
    fun `given user identifier and role 'PUT users {id}' must change user role in repository and return changed user`() {
        wheneverBlocking(userRepository) { findById(1) }
            .thenReturn(UserEntity(id = 1, username = "admin", passwordHash = "hash1", role = Role.ADMIN.name))
        wheneverBlocking(userRepository) { update(any()) }
            .thenAnswer(CopyUser)

        testApplication {
            application(withRoute { editUserRoute() })
            val response = client.put("/users/1") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                val form = EditUserPayload(role = Role.USER)
                setBody(Json.encodeToString(EditUserPayload.serializer(), form))
            }
            verifyBlocking(userRepository) { update(USER_ADMIN.copy(role = "USER")) }
            assertEquals(HttpStatusCode.OK, response.status)
            val body: UserView = response.assertResponseNotNull(UserView.serializer())
            assertEquals(Role.USER, body.role)
        }
    }

    @Test
    fun `given user identifier 'DELETE users {id}' must delete that user in repository`() {
        wheneverBlocking(userRepository) { findById(1) }
            .thenReturn(USER_ADMIN)

        testApplication {
            application(withRoute { deleteUserRoute() })
            val response = client.delete("/users/1") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }
            assertEquals(HttpStatusCode.OK, response.status)
            verifyBlocking(userRepository) { deleteById(1) }
        }
    }

    @Test
    fun `given user identifier 'PATCH users {id} block' must block that user in repository`() {
        wheneverBlocking(userRepository) { findById(1) }
            .thenReturn(USER_ADMIN)

        testApplication {
            application(withRoute { blockUserRoute() })
            val response = client.patch("/users/1/block") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }
            assertEquals(HttpStatusCode.OK, response.status)
            verifyBlocking(userRepository) { update(USER_ADMIN.copy(blocked = true)) }
        }
    }

    @Test
    fun `given user identifier 'PATCH users {id} unblock' must unblock that user in repository`() {
        wheneverBlocking(userRepository) { findById(1) }
            .thenReturn(USER_ADMIN.copy(blocked = true))

        testApplication {
            application(withRoute { unblockUserRoute() })
            val response = client.patch("/users/1/unblock") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }
            assertEquals(HttpStatusCode.OK, response.status)
            verifyBlocking(userRepository) { update(USER_ADMIN.copy(blocked = false)) }
        }
    }

    @Test
    fun `given user identifier 'PATCH users {id} reset-password' must generate and return a new password of that user`() {
        wheneverBlocking(userRepository) { findById(1) }
            .thenReturn(USER_ADMIN)
        whenever(passwordGenerator.generatePassword())
            .thenReturn("newsecret")
        whenever(passwordService.hashPassword("newsecret"))
            .thenReturn("newhash")

        testApplication {
            application(withRoute { resetPasswordRoute() })
            val response = client.patch("/users/1/reset-password") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }
            assertEquals(HttpStatusCode.OK, response.status)
            verifyBlocking(userRepository) { update(USER_ADMIN.copy(passwordHash = "newhash")) }
        }
    }

    @Test
    fun `given user identifier equal to current user, 'DELETE users {id}' must fail`() {
        testApplication {
            application(withRoute {
                authenticate {
                    deleteUserRoute()
                }
            })
            val response = client.delete("/users/123") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                addJwtToken("foo", userId = 123)
            }
            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        }
    }

    @Test
    fun `given user identifier equal to current user, 'PATCH users {id} block' must fail`() {
        testApplication {
            application(withRoute {
                authenticate {
                    blockUserRoute()
                }
            })
            val response = client.patch("/users/123/block") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                addJwtToken("foo", userId = 123)
            }
            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        }
    }

    @Test
    fun `given user identifier equal to current user, 'PUT users {id}' must fail`() {
        testApplication {
            application(withRoute {
                authenticate {
                    editUserRoute()
                }
            })
            val response = client.put("/users/123") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                val form = EditUserPayload(role = Role.USER)
                setBody(Json.encodeToString(EditUserPayload.serializer(), form))
                addJwtToken("foo", userId = 123)
            }
            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        }
    }

    @Test
    fun `given external user, 'PATCH users {id} reset-password' must fail`() {
        wheneverBlocking(userRepository) { findById(123) }
            .thenReturn(
                UserEntity(id = 123, username = "external-user", passwordHash = null, role = "USER")
            )

        testApplication {
            application(withRoute { resetPasswordRoute() })
            val response = client.patch("/users/123/reset-password") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }
            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        }
    }

    @Test
    fun `given external user with external role management, 'PUT users {id}' must fail`() {
        wheneverBlocking(userRepository) { findById(123) }
            .thenReturn(
                UserEntity(id = 123, username = "external-user", passwordHash = null, role = "USER")
            )

        testApplication {
            application {
                install(Resources)
                install(ContentNegotiation) {
                    json()
                }
                di {
                    bind<UserRepository>() with eagerSingleton { userRepository }
                    bind<PasswordService>() with eagerSingleton { passwordService }
                    bind<PasswordGenerator>() with eagerSingleton { passwordGenerator }
                    bind<UserManagementService>() with eagerSingleton {
                        UserManagementServiceImpl(
                            userRepository = instance(),
                            passwordService = instance(),
                            passwordGenerator = instance(),
                            externalRoleManagement = true
                        )
                    }
                }
                install(StatusPages) {
                    authStatusPages()
                }
                routing {
                    editUserRoute()
                }
            }
            val response = client.put("/users/123") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                val form = EditUserPayload(role = Role.ADMIN)
                setBody(Json.encodeToString(EditUserPayload.serializer(), form))
            }
            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        }
    }

    private fun withRoute(route: Route.() -> Unit): Application.() -> Unit = {
        install(Resources)
        install(ContentNegotiation) {
            json()
        }
        di {
            bind<UserRepository>() with eagerSingleton { userRepository }
            bind<PasswordService>() with eagerSingleton { passwordService }
            bind<PasswordGenerator>() with eagerSingleton { passwordGenerator }
            bind<UserManagementService>() with eagerSingleton {
                UserManagementServiceImpl(
                    userRepository = instance(),
                    passwordService = instance(),
                    passwordGenerator = instance(),
                )
            }
        }
        install(StatusPages) {
            authStatusPages()
        }
        install(Authentication) {
            jwtMock()
        }
        routing {
            route()
        }
    }
}