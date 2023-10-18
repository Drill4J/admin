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

import com.epam.drill.admin.auth.jwt.JwtConfig
import com.epam.drill.admin.auth.jwt.JwtTokenService
import com.epam.drill.admin.auth.jwt.generateSecret
import com.epam.drill.admin.auth.entity.Role
import com.epam.drill.admin.auth.service.UserAuthenticationService
import com.epam.drill.admin.auth.view.LoginPayload
import com.epam.drill.admin.auth.view.UserView
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import org.kodein.di.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import java.util.*
import kotlin.test.*

class SecurityConfigTest {

    private val testSecret = generateSecret()
    @Mock
    lateinit var authService: UserAuthenticationService

    @BeforeTest
    fun setup() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `given user with basic auth, request basic-only must succeed`() {
        whenever(authService.signIn(LoginPayload(username = "admin", password = "secret")))
            .thenReturn(UserView(username = "admin", role = Role.ADMIN, blocked = false))
        withTestApplication(config()) {
            with(handleRequest(HttpMethod.Get, "/basic-only") {
                addBasicAuth("admin", "secret")
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    @Test
    fun `given user without basic auth, request basic-only must fail with 401 status`() {
        withTestApplication(config()) {
            with(handleRequest(HttpMethod.Get, "/basic-only") {
                //not to add basic auth
            }) {
                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
        }
    }

    @Test
    fun `given user with valid jwt token, request jwt-only must succeed`() {
        withTestApplication(config()) {
            with(handleRequest(HttpMethod.Get, "/jwt-only") {
                addJwtToken(
                    username = "admin",
                    secret = testSecret)
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    @Test
    fun `given user without jwt token, request jwt-only must fail with 401 status`() {
        withTestApplication(config()) {
            with(handleRequest(HttpMethod.Get, "/jwt-only") {
                //not to add jwt token
            }) {
                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
        }
    }

    @Test
    fun `given user with expired jwt token, request jwt-only must fail with 401 status`() {
        withTestApplication(config()) {
            with(handleRequest(HttpMethod.Get, "/jwt-only") {
                addJwtToken(
                    username = "admin",
                    secret = testSecret,
                    expiresAt = Date(System.currentTimeMillis() - 1000)
                )
            }) {
                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
        }
    }

    @Test
    fun `given user with invalid jwt token, request jwt-only must fail with 401 status`() {
        withTestApplication(config()) {
            with(handleRequest(HttpMethod.Get, "/jwt-only") {
                addJwtToken(
                    username = "admin",
                    secret = "wrong_secret"
                )
            }) {
                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
        }
    }

    private fun config() = testModule(
        environment = {
            put("drill.jwt.issuer", "test issuer")
            put("drill.jwt.lifetime", "1m")
            put("drill.jwt.audience", "test audience")
            put("drill.jwt.secret", testSecret)
        },
        routing = {
            authenticate("jwt") {
                get("/jwt-only") {
                    call.respond(HttpStatusCode.OK)
                }
            }
            authenticate("basic") {
                get("/basic-only") {
                    call.respond(HttpStatusCode.OK)
                }
            }
        },
        bindings = {
            bind<JwtTokenService>() with singleton { JwtTokenService(JwtConfig(di)) }
            bind<SecurityConfig>() with eagerSingleton { SecurityConfig(di) }
            bind<UserAuthenticationService>() with provider { authService }
        }
    )
}