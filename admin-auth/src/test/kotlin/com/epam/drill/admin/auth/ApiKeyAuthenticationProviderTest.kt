package com.epam.drill.admin.auth

import com.epam.drill.admin.auth.config.API_KEY_HEADER
import com.epam.drill.admin.auth.config.apiKey
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
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


    private fun Route.configureGetApiRoute() {
        get("/api") {
            call.respond(HttpStatusCode.OK)
        }
    }

    private fun Authentication.Configuration.configureSimpleApiKey(
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