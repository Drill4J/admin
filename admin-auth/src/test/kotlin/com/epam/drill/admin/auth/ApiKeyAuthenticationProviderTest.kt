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

import com.epam.drill.admin.auth.config.API_KEY_HEADER
import com.epam.drill.admin.auth.config.apiKey
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiKeyAuthenticationProviderTest {

    @Test
    fun `given correct api key, authenticated request must succeed`() {
        val testApiKey = "test-api-key"

        withTestApplication({
            install(Authentication) {
                configureSimpleApiKey(testApiKey)
            }
            routing {
                authenticate {
                    configureGetApiRoute()
                }
            }
        }) {
            with(handleRequest(HttpMethod.Get, "/api") {
                addHeader(API_KEY_HEADER, testApiKey)
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    @Test
    fun `given incorrect api key, authenticated request must fail`() {
        withTestApplication({
            install(Authentication) {
                configureSimpleApiKey( "correct-api-key")
            }
            routing {
                authenticate {
                    configureGetApiRoute()
                }
            }
        }) {
            with(handleRequest(HttpMethod.Get, "/api") {
                addHeader(API_KEY_HEADER, "incorrect-api-key")
            }) {
                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
        }
    }

    @Test
    fun `given api key challenge function, authenticated request must fail with specified error`() {
        val testFailError = HttpStatusCode.BadRequest

        withTestApplication({
            install(Authentication) {
                apiKey {
                    validate { null }
                    challenge { call ->
                        call.respond(testFailError)
                    }
                }
            }
            routing {
                authenticate {
                    configureGetApiRoute()
                }
            }
        }) {
            with(handleRequest(HttpMethod.Get, "/api")) {
                assertEquals(testFailError, response.status())
            }
        }
    }

    @Test
    fun `given custom api key header, authenticated request must succeed in retrieving api key from that header`() {
        val testHeader = "X-Test-Api-Key"
        val testApiKey = "test-api-key"
        withTestApplication({
            install(Authentication) {
                apiKey {
                    headerName = testHeader
                    validate { apiKey ->
                        apiKey
                            .takeIf { it == testApiKey }
                            ?.let { UserIdPrincipal("username") }
                    }
                }
            }
            routing {
                authenticate {
                    configureGetApiRoute()
                }
            }
        }) {
            with(handleRequest(HttpMethod.Get, "/api") {
                addHeader(testHeader, testApiKey)
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }


    private fun Route.configureGetApiRoute() {
        get("/api") {
            call.respond(HttpStatusCode.OK)
        }
    }

    private fun AuthenticationConfig.configureSimpleApiKey(
        testApiKey: String,
        username: String = "test-user",
        configName: String? = null
    ) {
        apiKey(configName) {
            validate { apiKey ->
                apiKey
                    .takeIf { it == testApiKey }
                    ?.let { UserIdPrincipal(username) }
            }
        }
    }
}