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

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.epam.drill.admin.auth.config.JWT_COOKIE
import com.epam.drill.admin.auth.config.SESSION_COOKIE
import com.epam.drill.admin.auth.config.oauthModule
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
import org.kodein.di.singleton
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.net.URL
import kotlin.test.*

typealias RequestHandler = Pair<String, suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData>

class OAuthModuleTest {

    private val testOAuthServerHost = "some-oauth-server.com"
    private val testDrillHost = "example.com"
    private val testClientId = "test-client"
    private val testClientSecret = "test-secret"
    private val keyPair = generateRSAKeyPair()
    private val rsa256 = Algorithm.RSA256(keyPair.public as RSAPublicKey, keyPair.private as RSAPrivateKey)

    @Mock
    lateinit var mockJwkProvider: JwkProvider
    @Mock
    lateinit var jwk: Jwk

    @BeforeTest
    fun setup() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `given valid oauth2 token, jwt protected request must succeed`() {
        val testUsername = "test-user"
        val testJwkKey = "test-jwk"
        val testIssuer = "test-issuer"

        withTestApplication({
            environment {
                put("drill.auth.oauth2.issuer", testIssuer)
            }
            oauthModule {
                mockJwkProvider()
            }
            routing {
                authenticate("jwt") {
                    get("/protected") {
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }) {
            whenever(mockJwkProvider.get(testJwkKey)).thenReturn(jwk)
            whenever(jwk.publicKey).thenReturn(keyPair.public)
            whenever(jwk.algorithm).thenReturn("RS256")

            with(handleRequest(HttpMethod.Get, "/protected") {
                addJwtToken(
                    username = testUsername,
                    issuer = testIssuer,
                    algorithm = rsa256
                ) {
                    withHeader(mapOf("kid" to testJwkKey))
                    withClaim("preferred_username", testUsername)
                    withClaim(
                        "realm_access",
                        mapOf("roles" to listOf("USER"))
                    ) //TODO remove after adding claim mapping tests
                }
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    @Test
    fun `oauth login request must be redirected to oauth2 authorize url`() {
        withTestApplication({
            oauthTestEnvironment()
            oauthModule()
        }) {
            with(handleRequest(HttpMethod.Get, "/oauth/login")) {
                assertEquals(HttpStatusCode.Found, response.status())

                val url = URL(response.headers[HttpHeaders.Location])
                val queryParams = url.query?.split("&")?.associate {
                    val (key, value) = it.split("=")
                    key to value
                } ?: emptyMap()
                assertEquals(testOAuthServerHost, url.host)
                assertEquals("/authorizeUrl", url.path)
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
        val testAuthenticationCode = "test-code"
        val testState = "test-state"
        val testAccessToken = JWT.create()
            .withClaim("preferred_username", testUsername)
            .withClaim("realm_access", mapOf("roles" to listOf("user")))  //TODO remove after adding claim mapping tests
            .sign(rsa256)

        withTestApplication({
            oauthTestEnvironment()
            oauthModule {
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
                assertNotNull(response.cookies[SESSION_COOKIE])
                assertNotNull(response.cookies[JWT_COOKIE]).let { jwtCookie ->
                    assertEquals(testAccessToken, jwtCookie.value)
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

    private fun Application.oauthTestEnvironment() {
        environment {
            put("drill.auth.oauth2.authorizeUrl", "http://$testOAuthServerHost/authorizeUrl")
            put("drill.auth.oauth2.accessTokenUrl", "http://$testOAuthServerHost/accessTokenUrl")
            put("drill.auth.oauth2.userInfoUrl", "http://$testOAuthServerHost/userInfoUrl")
            put("drill.auth.oauth2.jwkSetUrl", "http://$testOAuthServerHost/jwkSetUrl")
            put("drill.auth.oauth2.clientId", testClientId)
            put("drill.auth.oauth2.clientSecret", testClientSecret)
            put("drill.auth.oauth2.scopes", "scope1, scope2")
            put("drill.auth.oauth2.issuer", "test-issuer")
            put("drill.ui.rootUrl", "http://$testDrillHost/drill")
            put("drill.ui.rootPath", "/drill")
        }
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

    private fun DI.MainBuilder.mockJwkProvider() {
        bind<JwkProvider>(overrides = true) with singleton { mockJwkProvider }
    }
}