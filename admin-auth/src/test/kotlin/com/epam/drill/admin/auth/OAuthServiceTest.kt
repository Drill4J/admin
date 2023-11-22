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
import com.epam.drill.admin.auth.config.OAuthAccessDeniedException
import com.epam.drill.admin.auth.config.OAuthConfig
import com.epam.drill.admin.auth.config.OAuthUnauthorizedException
import com.epam.drill.admin.auth.entity.UserEntity
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.repository.UserRepository
import com.epam.drill.admin.auth.service.impl.OAuthServiceImpl
import io.ktor.auth.*
import io.ktor.client.engine.mock.*
import io.ktor.config.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.*

/**
 * Testing [OAuthServiceImpl]
 */
class OAuthServiceTest {
    @Mock
    lateinit var userRepository: UserRepository
    private val testAlgorithm = Algorithm.HMAC512("secret")

    @BeforeTest
    fun setup() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `given user, authenticating with OAuth2 first time, signInThroughOAuth must create user entity with data from OAuth2`(): Unit =
        runBlocking {
            val testUsername = "some-username"
            val testAccessToken = JWT.create()
                .withSubject(testUsername)
                .sign(testAlgorithm)
            val oauthService = OAuthServiceImpl(mockHttpClient(), OAuthConfig(MapApplicationConfig()), userRepository)
            whenever(userRepository.findByUsername(testUsername)).thenReturn(null)
            whenever(userRepository.create(any())).thenReturn(123)

            oauthService.signInThroughOAuth(withPrincipal(testAccessToken))
            verify(userRepository).create(UserEntity(username = testUsername, role = Role.UNDEFINED.name))
        }

    @Test
    fun `given existing user, authenticating with OAuth2, signInThroughOAuth must update user entity with data from OAuth2`() =
        runBlocking {
            val testUsername = "some-username"
            val testAccessToken = JWT.create()
                .withSubject(testUsername)
                .sign(testAlgorithm)
            val oauthService = OAuthServiceImpl(mockHttpClient(), OAuthConfig(MapApplicationConfig()), userRepository)
            whenever(userRepository.findByUsername(testUsername)).thenReturn(
                UserEntity(id = 123, username = testUsername, role = Role.USER.name)
            )

            oauthService.signInThroughOAuth(withPrincipal(testAccessToken))
            verify(userRepository).update(UserEntity(id = 123, username = testUsername, role = Role.USER.name))
        }

    @Test
    fun `given blocked user, authenticating with OAuth2, signInThroughOAuth must fail`(): Unit =
        runBlocking {
            val testUsername = "some-username"
            val testAccessToken = JWT.create()
                .withSubject(testUsername)
                .sign(testAlgorithm)
            val oauthService = OAuthServiceImpl(mockHttpClient(), OAuthConfig(MapApplicationConfig()), userRepository)
            whenever(userRepository.findByUsername(testUsername)).thenReturn(
                UserEntity(
                    id = 123,
                    username = testUsername,
                    role = Role.USER.name,
                    blocked = true
                )
            )

            assertThrows<OAuthAccessDeniedException> {
                oauthService.signInThroughOAuth(withPrincipal(testAccessToken))
            }
        }

    @Test
    fun `if user-info request fails, signInThroughOAuth must fail`(): Unit =
        runBlocking {
            val config = MapApplicationConfig().apply {
                put("drill.auth.oauth2.userInfoUrl", "http://some-oauth-server.com/userInfoUrl")
            }
            val httpClient = mockHttpClient(
                "/userInfoUrl" shouldRespond {
                    respondError(HttpStatusCode.Unauthorized, "Invalid token")
                }
            )
            val oauthService = OAuthServiceImpl(httpClient, OAuthConfig(config), userRepository)

            assertThrows<OAuthUnauthorizedException> {
                oauthService.signInThroughOAuth(withPrincipal("invalid-token"))
            }
        }

    @Test
    fun `signInThroughOAuth must extract user data from access token`(): Unit =
        runBlocking {
            val testUsername = "some-username"
            val testAccessToken = JWT.create()
                .withClaim("login", testUsername)
                .withClaim("authorities", listOf("one-role", "Dev", "another-role"))
                .sign(testAlgorithm)

            val config = MapApplicationConfig().apply {
                put("drill.auth.oauth2.tokenMapping.username", "login")
                put("drill.auth.oauth2.tokenMapping.roles", "authorities")
                put("drill.auth.oauth2.roleMapping.user", "DEV")
                put("drill.auth.oauth2.roleMapping.admin", "OPS")
            }
            val oauthService = OAuthServiceImpl(mockHttpClient(), OAuthConfig(config), userRepository)
            whenever(userRepository.findByUsername(testUsername))
                .thenReturn(UserEntity(id = 123, username = testUsername, role = Role.UNDEFINED.name))
            whenever(userRepository.update(any())).thenReturn(Unit)

            val userInfo = oauthService.signInThroughOAuth(withPrincipal(testAccessToken))
            assertEquals(testUsername, userInfo.username)
            assertEquals(Role.USER, userInfo.role)
        }

    @Test
    fun `signInThroughOAuth must extract user data from user-info response`(): Unit =
        runBlocking {
            val testUsername = "some-username"

            val config = MapApplicationConfig().apply {
                put("drill.auth.oauth2.userInfoUrl", "http://some-oauth-server.com/userInfoUrl")
                put("drill.auth.oauth2.userInfoMapping.username", "user_name")
                put("drill.auth.oauth2.userInfoMapping.roles", "realm_roles")
                put("drill.auth.oauth2.roleMapping.user", "DEV")
                put("drill.auth.oauth2.roleMapping.admin", "OPS")
            }
            val httpClient = mockHttpClient(
                "/userInfoUrl" shouldRespond {
                    respondOk(
                        """
                    {                              
                      "user_name":"$testUsername",
                      "realm_roles":["one-role", "Dev", "another-role"]                             
                    }     
                    """.trimIndent()
                    )
                }
            )
            val oauthService = OAuthServiceImpl(httpClient, OAuthConfig(config), userRepository)
            whenever(
                userRepository.findByUsername(testUsername)
            ).thenReturn(
                UserEntity(id = 123, username = testUsername, role = Role.UNDEFINED.name)
            )
            whenever(userRepository.update(any())).thenReturn(Unit)

            val userInfo = oauthService.signInThroughOAuth(withPrincipal("test-access-token"))
            assertEquals(testUsername, userInfo.username)
            assertEquals(Role.USER, userInfo.role)
        }

    private fun withPrincipal(testAccessToken: String) = OAuthAccessTokenResponse.OAuth2(
        accessToken = testAccessToken,
        tokenType = "Bearer",
        expiresIn = 3600,
        refreshToken = null
    )

}

