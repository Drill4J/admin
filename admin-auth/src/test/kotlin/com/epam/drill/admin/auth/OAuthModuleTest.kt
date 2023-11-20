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
import com.epam.drill.admin.auth.config.*
import com.epam.drill.admin.auth.principal.Role
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.ktor.closestDI
import org.kodein.di.ktor.di
import org.kodein.di.singleton
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.net.URL
import java.util.*
import kotlin.test.*

typealias RequestHandler = Pair<String, suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData>

class OAuthModuleTest {

    private val testOAuthServerHost = "some-oauth-server.com"
    private val testDrillHost = "example.com"
    private val testClientId = "test-client"
    private val testClientSecret = "test-secret"
    private val keyPair = generateRSAKeyPair()
    private val rsa256 = Algorithm.RSA256(keyPair.public as RSAPublicKey, keyPair.private as RSAPrivateKey)

    @Test
    fun `given valid jwt cookie, jwt protected request must succeed`() {
        val testUsername = "test-user"
        val testIssuer = "test-issuer"
        val testSecret = "secret"

        withTestApplication({
            environment {
                put("drill.auth.jwt.issuer", testIssuer)
                put("drill.auth.jwt.secret", testSecret)
            }
            withTestOAuthModule()
            routing {
                authenticate("jwt") {
                    get("/protected") {
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }) {
            with(handleRequest(HttpMethod.Get, "/protected") {
                addJwtToken(
                    username = testUsername,
                    issuer = testIssuer,
                    secret = testSecret) {
                    addHeader("Cookie", "$JWT_COOKIE=$it")
                }
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    @Test
    fun `oauth login request must be redirected to oauth2 authorize url`() {
        withTestApplication({
            environment {
                put("drill.auth.oauth2.authorizeUrl", "http://$testOAuthServerHost/authorizeUrl")
                put("drill.auth.oauth2.accessTokenUrl", "http://$testOAuthServerHost/accessTokenUrl")
                put("drill.auth.oauth2.clientId", testClientId)
                put("drill.auth.oauth2.clientSecret", testClientSecret)
                put("drill.auth.oauth2.scopes", "scope1, scope2")
                put("drill.ui.rootUrl", "http://$testDrillHost/drill")
            }
            withTestOAuthModule()
        }) {
            with(handleRequest(HttpMethod.Get, "/oauth/login")) {
                assertEquals(HttpStatusCode.Found, response.status())

                val redirectedUrl = URL(response.headers[HttpHeaders.Location])
                val queryParams = redirectedUrl.queryParams()
                assertEquals(testOAuthServerHost, redirectedUrl.host)
                assertEquals("/authorizeUrl", redirectedUrl.path)
                assertEquals(testClientId, queryParams["client_id"])
                assertEquals("http://$testDrillHost/oauth/callback", queryParams["redirect_uri"])
                assertEquals("code", queryParams["response_type"])
                assertNotNull(queryParams["state"])
            }
        }
    }

    @Test
    fun `given valid authentication code, oauth callback request must set cookies and redirect to root url`() {
        val testUsername = "test-user"
        val testIssuer = "test-issuer"
        val testAuthenticationCode = "test-code"
        val testState = "test-state"
        val testAccessToken = JWT.create()
            .withClaim("preferred_username", testUsername)
            .withClaim("realm_access", mapOf("roles" to listOf("user")))  //TODO remove after adding claim mapping tests
            .sign(rsa256)

        withTestApplication({
            environment {
                put("drill.auth.oauth2.authorizeUrl", "http://$testOAuthServerHost/authorizeUrl")
                put("drill.auth.oauth2.accessTokenUrl", "http://$testOAuthServerHost/accessTokenUrl")
                put("drill.auth.oauth2.userInfoUrl", "http://$testOAuthServerHost/userInfoUrl")
                put("drill.auth.oauth2.clientId", testClientId)
                put("drill.auth.oauth2.clientSecret", testClientSecret)
                put("drill.auth.jwt.issuer", testIssuer)
                put("drill.ui.rootUrl", "http://$testDrillHost/drill")
                put("drill.ui.rootPath", "/drill")
            }
            withTestOAuthModule {
                mockHttpClient("oauthHttpClient",
                    "/accessTokenUrl" to { request ->
                        request.formData().apply {
                            assertEquals(testClientId, this["client_id"])
                            assertEquals(testClientSecret, this["client_secret"])
                            assertEquals(testAuthenticationCode, this["code"])
                            assertEquals(testState, this["state"])
                        }
                        respondOk(
                            """
                            {
                              "access_token":"$testAccessToken",                                                            
                              "refresh_token":"test-refresh-token"                              
                            }
                            """.trimIndent()
                        )
                    },
                    "/userInfoUrl" to { request ->
                        assertEquals("Bearer $testAccessToken", request.headers[HttpHeaders.Authorization])
                        respondOk(
                            """
                            {                              
                              "preferred_username":"$testUsername",
                              "roles":["user"]                             
                            }     
                            """.trimIndent()
                        )
                    }
                )
            }
        }) {
            with(handleRequest(HttpMethod.Get, "/oauth/callback?code=$testAuthenticationCode&state=$testState")) {
                assertEquals(HttpStatusCode.Found, response.status())
                assertEquals("http://$testDrillHost/drill", response.headers[HttpHeaders.Location])
                assertNotNull(response.cookies[JWT_COOKIE]).let { jwtCookie ->
                    assertNotNull(jwtCookie.value)
                    JWT.decode(jwtCookie.value).apply {
                        assertEquals(testUsername, subject)
                        assertEquals(testIssuer, issuer)
                        assertEquals(Role.USER.name, getClaim("role").asString())
                    }
                    assertTrue(jwtCookie.httpOnly)
                    assertEquals("/drill", jwtCookie.path)
                }
            }
        }
    }

    private fun generateRSAKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        return keyPairGenerator.generateKeyPair()
    }

    private fun DI.MainBuilder.mockHttpClient(tag: String, vararg requestHandlers: RequestHandler) {
        bind<HttpClient>(tag, overrides = true) with singleton {
            HttpClient(MockEngine { request ->
                requestHandlers
                    .find { request.url.encodedPath == it.first }
                    ?.runCatching { this@MockEngine.second(request) }
                    ?.getOrElse { exception ->
                        respondError(HttpStatusCode.BadRequest, exception.message ?: "${exception::class} error")
                    }
                    ?: respondBadRequest()
            })
        }
    }

    private fun Application.withTestOAuthModule(configureDI: DI.MainBuilder.() -> Unit = {}) {
        di {
            import(oauthDIModule)
            configureDI()
        }

        install(Authentication) {
            configureOAuthAuthentication(closestDI())
        }

        routing {
            configureOAuthRoutes()
        }
    }

}