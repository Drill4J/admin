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

import com.epam.drill.admin.auth.entity.UserEntity
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.repository.UserRepository
import com.epam.drill.admin.auth.route.authStatusPages
import com.epam.drill.admin.auth.route.updatePasswordRoute
import com.epam.drill.admin.auth.route.userAuthenticationRoutes
import com.epam.drill.admin.auth.service.PasswordService
import com.epam.drill.admin.auth.service.TokenService
import com.epam.drill.admin.auth.service.UserAuthenticationService
import com.epam.drill.admin.auth.service.impl.UserAuthenticationServiceImpl
import com.epam.drill.admin.auth.model.ChangePasswordPayload
import com.epam.drill.admin.auth.model.LoginPayload
import com.epam.drill.admin.auth.model.RegistrationPayload
import com.epam.drill.admin.auth.model.TokenView
import com.epam.drill.admin.auth.principal.User
import com.epam.drill.admin.auth.route.userProfileRoutes
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.application.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.testing.*
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
import kotlin.test.*

val USER_GUEST
    get() = UserEntity(id = 1, username = "guest", passwordHash = "hash", role = "UNDEFINED").copy()

/**
 * Testing /sign-in, /sign-up and /reset-password routers and UserAuthenticationServiceImpl
 */
class UserAuthenticationTest {

    @Mock
    lateinit var userRepository: UserRepository

    @Mock
    lateinit var passwordService: PasswordService

    @Mock
    lateinit var tokenService: TokenService

    @BeforeTest
    fun setup() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `given correct username and password 'POST sign-in' must return an access token`() {
        wheneverBlocking(userRepository) { findByUsername("guest") }
            .thenReturn(USER_GUEST)
        whenever(passwordService.matchPasswords("secret", "hash"))
            .thenReturn(true)
        whenever(tokenService.issueToken(any())).thenReturn("token")

        withTestApplication(config) {
            with(handleRequest(HttpMethod.Post, "/sign-in") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                val payload = LoginPayload(username = "guest", password = "secret")
                setBody(Json.encodeToString(LoginPayload.serializer(), payload))
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
                val response = assertResponseNotNull(TokenView.serializer())
                assertEquals("token", response.token)
            }
        }
    }

    @Test
    fun `given unique username 'POST sign-up' must succeed and user must be created`() {
        wheneverBlocking(userRepository) { findByUsername("foobar") }
            .thenReturn(null)
        whenever(passwordService.hashPassword("secret"))
            .thenReturn("hash")
        wheneverBlocking(userRepository) { create(any()) }
            .thenReturn(1)

        withTestApplication(config) {
            with(handleRequest(HttpMethod.Post, "/sign-up") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                val form = RegistrationPayload(username = "foobar", password = "secret")
                setBody(Json.encodeToString(RegistrationPayload.serializer(), form))
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
                verifyBlocking(userRepository) {
                    create(
                        UserEntity(
                            username = "foobar",
                            passwordHash = "hash",
                            role = Role.UNDEFINED.name
                        )
                    )
                }
            }
        }
    }

    @Test
    fun `given correct old password 'POST update-password' must succeed`() {
        wheneverBlocking(userRepository) { findByUsername("guest") }
            .thenReturn(USER_GUEST)
        whenever(passwordService.matchPasswords("secret", "hash"))
            .thenReturn(true)
        whenever(passwordService.hashPassword("secret2"))
            .thenReturn("hash2")

        withTestApplication(config) {
            with(handleRequest(HttpMethod.Post, "/update-password") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                addBasicAuth("guest", "secret")
                val form = ChangePasswordPayload(oldPassword = "secret", newPassword = "secret2")
                setBody(Json.encodeToString(ChangePasswordPayload.serializer(), form))
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
                verifyBlocking(userRepository) { update(USER_GUEST.copy(passwordHash = "hash2")) }
            }
        }
    }

    @Test
    fun `given incorrect username 'POST sign-in' must fail with 401 status`() {
        wheneverBlocking(userRepository) { findByUsername("unknown") }
            .thenReturn(null)

        withTestApplication(config) {
            with(handleRequest(HttpMethod.Post, "/sign-in") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                val form = LoginPayload(username = "unknown", password = "secret")
                setBody(Json.encodeToString(LoginPayload.serializer(), form))
            }) {
                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
        }
    }

    @Test
    fun `given incorrect password 'POST sign-in' must fail with 401 status`() {
        wheneverBlocking(userRepository) { findByUsername("guest") }
            .thenReturn(USER_GUEST)
        whenever(passwordService.matchPasswords("incorrect", "hash"))
            .thenReturn(false)

        withTestApplication(config) {
            with(handleRequest(HttpMethod.Post, "/sign-in") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                val form = LoginPayload(username = "guest", password = "incorrect")
                setBody(Json.encodeToString(LoginPayload.serializer(), form))
            }) {
                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
        }
    }

    @Test
    fun `given username of blocked user 'POST sign-in' must fail with 403 status`() {
        wheneverBlocking(userRepository) { findByUsername("blocked_user") }
            .thenReturn(
                UserEntity(
                    id = 1, username = "blocked_user", passwordHash = "hash", role = "USER", blocked = true
                )
            )
        whenever(passwordService.matchPasswords("secret", "hash"))
            .thenReturn(true)

        withTestApplication(config) {
            with(handleRequest(HttpMethod.Post, "/sign-in") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                val form = LoginPayload(username = "blocked_user", password = "secret")
                setBody(Json.encodeToString(LoginPayload.serializer(), form))
            }) {
                assertEquals(HttpStatusCode.Forbidden, response.status())
            }
        }
    }

    @Test
    fun `given already existing username 'POST sign-up' must fail with 400 status`() {
        wheneverBlocking(userRepository) { findByUsername("guest") }
            .thenReturn(USER_GUEST)

        withTestApplication(config) {
            with(handleRequest(HttpMethod.Post, "/sign-up") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                val form = RegistrationPayload(username = "guest", password = "secret")
                setBody(Json.encodeToString(RegistrationPayload.serializer(), form))
            }) {
                assertEquals(HttpStatusCode.BadRequest, response.status())
            }
        }
    }

    @Test
    fun `without authentication 'POST update-password' must fail with 401 status`() {
        withTestApplication(config) {
            with(handleRequest(HttpMethod.Post, "/update-password") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                //not add auth
                val form = ChangePasswordPayload(oldPassword = "secret", newPassword = "secret2")
                setBody(Json.encodeToString(ChangePasswordPayload.serializer(), form))
            }) {
                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
        }
    }

    @Test
    fun `given incorrect old password 'POST update-password' must fail with 400 status`() {
        wheneverBlocking(userRepository) { findByUsername("guest") }
            .thenReturn(USER_GUEST)
        whenever(passwordService.matchPasswords("incorrect", "hash"))
            .thenReturn(false)

        withTestApplication(config) {
            with(handleRequest(HttpMethod.Post, "/update-password") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                addBasicAuth("guest", "secret")
                val form = ChangePasswordPayload(oldPassword = "incorrect", newPassword = "secret2")
                setBody(Json.encodeToString(ChangePasswordPayload.serializer(), form))
            }) {
                assertEquals(HttpStatusCode.BadRequest, response.status())
            }
        }
    }

    private val config: Application.() -> Unit = {
        install(Locations)
        install(ContentNegotiation) {
            json()
        }
        install(StatusPages) {
            authStatusPages()
        }
        install(Authentication) {
            basic {
                validate {
                    User(it.name, Role.UNDEFINED)
                }
            }
        }
        di {
            bind<UserRepository>() with eagerSingleton { userRepository }
            bind<PasswordService>() with eagerSingleton { passwordService }
            bind<TokenService>() with eagerSingleton { tokenService }
            bind<UserAuthenticationService>() with eagerSingleton {
                UserAuthenticationServiceImpl(
                    instance(),
                    instance()
                )
            }
        }
        routing {
            userAuthenticationRoutes()
            authenticate {
                userProfileRoutes()
            }
        }
    }
}
