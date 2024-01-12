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
import com.epam.drill.admin.auth.config.*
import com.epam.drill.admin.auth.model.UserInfoView
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.service.OAuthService
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.ktor.closestDI
import org.kodein.di.ktor.di
import org.kodein.di.provider
import org.kodein.di.singleton
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import java.net.URL
import kotlin.test.*

/**
 * Testing /oauth routes, [configureOAuthAuthentication]
 */
class OAuthModuleTest {

    private val testOAuthServerHost = "some-oauth-server.com"
    private val testDrillHost = "example.com"
    private val testClientId = "test-client"
    private val testClientSecret = "test-secret"

    @Mock
    lateinit var mockOAuthService: OAuthService

    @BeforeTest
    fun setup() {
        MockitoAnnotations.openMocks(this)
    }

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
                    secret = testSecret
                ) {
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
                put("drill.auth.oauth2.redirectUrl", "http://$testDrillHost/drill")
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

        withTestApplication({
            environment {
                put("drill.auth.oauth2.authorizeUrl", "http://$testOAuthServerHost/authorizeUrl")
                put("drill.auth.oauth2.accessTokenUrl", "http://$testOAuthServerHost/accessTokenUrl")
                put("drill.auth.oauth2.clientId", testClientId)
                put("drill.auth.oauth2.clientSecret", testClientSecret)
                put("drill.auth.jwt.issuer", testIssuer)
                put("drill.auth.oauth2.redirectUrl", "http://$testDrillHost/drill")
            }
            withTestOAuthModule {
                bind<OAuthService>(overrides = true) with provider { mockOAuthService }
                mockHttpClient("oauthHttpClient",
                    "/accessTokenUrl" shouldRespond { request ->
                        request.formData().apply {
                            assertEquals(testClientId, this["client_id"])
                            assertEquals(testClientSecret, this["client_secret"])
                            assertEquals(testAuthenticationCode, this["code"])
                            assertEquals(testState, this["state"])
                        }
                        respondOk(
                            """
                            {
                              "access_token":"test-access-token",                                                            
                              "refresh_token":"test-refresh-token"                              
                            }
                            """.trimIndent()
                        )
                    }
                )
            }
        }) {
            wheneverBlocking(mockOAuthService) { signInThroughOAuth(any()) }.thenReturn(UserInfoView(id = 123, testUsername, Role.USER, false))

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
                    assertEquals("/", jwtCookie.path)
                }
            }
        }
    }

    @Test
    fun `given invalid authentication code, oauth callback request must fail with 401 status`() {
        val wrongAuthenticationCode = "invalid-code"
        val testState = "test-state"

        withTestApplication({
            environment {
                put("drill.auth.oauth2.authorizeUrl", "http://$testOAuthServerHost/authorizeUrl")
                put("drill.auth.oauth2.accessTokenUrl", "http://$testOAuthServerHost/accessTokenUrl")
                put("drill.auth.oauth2.clientId", testClientId)
                put("drill.auth.oauth2.clientSecret", testClientSecret)
                put("drill.auth.oauth2.redirectUrl", "http://$testDrillHost/drill")
            }
            withTestOAuthModule {
                bind<OAuthService>(overrides = true) with provider { mockOAuthService }
                mockHttpClient("oauthHttpClient",
                    "/accessTokenUrl" shouldRespond {
                        respondError(HttpStatusCode.Unauthorized, "Invalid authentication code")
                    }
                )
            }
        }) {
            with(handleRequest(HttpMethod.Get, "/oauth/callback?code=$wrongAuthenticationCode&state=$testState")) {
                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
        }
    }

    @Test
    fun `if signInThroughOAuth throws OAuthUnauthorizedException, oauth callback request must fail with 401 status`() {
        val testAuthenticationCode = "test-code"
        val testState = "test-state"

        withTestApplication({
            environment {
                put("drill.auth.oauth2.authorizeUrl", "http://$testOAuthServerHost/authorizeUrl")
                put("drill.auth.oauth2.accessTokenUrl", "http://$testOAuthServerHost/accessTokenUrl")
                put("drill.auth.oauth2.clientId", testClientId)
                put("drill.auth.oauth2.clientSecret", testClientSecret)
                put("drill.auth.oauth2.redirectUrl", "http://$testDrillHost/drill")
            }
            withTestOAuthModule {
                bind<OAuthService>(overrides = true) with provider { mockOAuthService }
                mockHttpClient("oauthHttpClient",
                    "/accessTokenUrl" shouldRespond { request ->
                        respondOk(
                            """
                            {
                              "access_token":"test-access-token",                                                            
                              "refresh_token":"test-refresh-token"                              
                            }
                            """.trimIndent()
                        )
                    }
                )
            }
        }) {
            wheneverBlocking(mockOAuthService) { signInThroughOAuth(any()) }.thenThrow(OAuthUnauthorizedException())
            with(handleRequest(HttpMethod.Get, "/oauth/callback?code=$testAuthenticationCode&state=$testState")) {
                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
        }
    }

    private fun DI.MainBuilder.mockHttpClient(tag: String, vararg requestHandlers: MockHttpRequest) {
        bind<HttpClient>(tag, overrides = true) with singleton {
            mockHttpClient(*requestHandlers)
        }
    }

    private fun Application.withTestOAuthModule(configureDI: DI.MainBuilder.() -> Unit = {}) {
        install(StatusPages) {
            oauthStatusPages()
        }

        di {
            import(oauthDIModule)
            configureDI()
        }

        install(Authentication) {
            configureJwtAuthentication(closestDI())
            configureOAuthAuthentication(closestDI())
        }

        routing {
            configureOAuthRoutes()
        }
    }

}