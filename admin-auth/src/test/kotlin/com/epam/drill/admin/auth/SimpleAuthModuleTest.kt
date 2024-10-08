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

import com.epam.drill.admin.auth.config.configureJwtAuthentication
import com.epam.drill.admin.auth.config.generateSecret
import com.epam.drill.admin.auth.config.simpleAuthDIModule
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.service.UserAuthenticationService
import com.epam.drill.admin.auth.model.LoginPayload
import com.epam.drill.admin.auth.model.UserInfoView
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.kodein.di.*
import org.kodein.di.ktor.closestDI
import org.kodein.di.ktor.di
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.util.*
import kotlin.test.*

class SimpleAuthModuleTest {

    private val testSecret = generateSecret()
    private val testIssuer = "test-issuer"

    @Mock
    lateinit var authService: UserAuthenticationService

    @BeforeTest
    fun setup() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `given user with valid jwt token, request jwt-only must succeed`() {
        withTestApplication(config) {
            with(handleRequest(HttpMethod.Get, "/jwt-only") {
                addJwtToken(
                    username = "admin",
                    issuer = testIssuer,
                    secret = testSecret,
                    configureHeader = { addHeader(HttpHeaders.Cookie, "jwt=$it;") }
                )
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    @Test
    fun `given user without jwt token, request jwt-only must fail with 401 status`() {
        withTestApplication(config) {
            with(handleRequest(HttpMethod.Get, "/jwt-only") {
                //not to add jwt token
            }) {
                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
        }
    }

    @Test
    fun `given user with expired jwt token, request jwt-only must fail with 401 status`() {
        withTestApplication(config) {
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
        withTestApplication(config) {
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

    private val config: Application.() -> Unit = {
        environment {
            put("drill.auth.jwt.issuer", testIssuer)
            put("drill.auth.jwt.lifetime", "1m")
            put("drill.auth.jwt.audience", "test audience")
            put("drill.auth.jwt.secret", testSecret)
        }
        withTestSimpleAuthModule {
            bind<UserAuthenticationService>(overrides = true) with provider { authService }
        }

        routing {
            authenticate("jwt") {
                get("/jwt-only") {
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }

    private fun Application.withTestSimpleAuthModule(configureDI: DI.MainBuilder.() -> Unit = {}) {
        di {
            import(simpleAuthDIModule)
            configureDI()
        }

        val di = closestDI()
        install(Authentication) {
            configureJwtAuthentication(di)
        }
    }
}