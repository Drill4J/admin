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

import com.epam.drill.admin.auth.config.OAuthAccessDeniedException
import com.epam.drill.admin.auth.config.OAuthConfig
import com.epam.drill.admin.auth.config.OAuthUnauthorizedException
import com.epam.drill.admin.auth.entity.UserEntity
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.repository.UserRepository
import com.epam.drill.admin.auth.service.OAuthMapper
import com.epam.drill.admin.auth.service.impl.OAuthServiceImpl
import io.ktor.auth.*
import io.ktor.client.engine.mock.*
import io.ktor.config.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.stubbing.Answer
import kotlin.test.*

/**
 * Testing [OAuthServiceImpl]
 */
class OAuthServiceTest {
    @Mock
    lateinit var userRepository: UserRepository
    @Mock
    lateinit var oauthMapper: OAuthMapper

    private val mockConfig = OAuthConfig(MapApplicationConfig())

    @BeforeTest
    fun setup() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `given user, authenticating with OAuth2 first time, signInThroughOAuth must create user entity with data from OAuth2`(): Unit =
        runBlocking {
            val testUsername = "some-username"
            val testAccessToken = "test-access-token"
            val testUserFromOAuth = UserEntity(username = testUsername, role = Role.UNDEFINED.name)
            val oauthService = OAuthServiceImpl(mockHttpClient(), mockConfig, userRepository, oauthMapper)
            whenever(oauthMapper.mapAccessTokenPayloadToUserEntity(testAccessToken)).thenReturn(testUserFromOAuth)
            whenever(userRepository.findByUsername(testUsername)).thenReturn(null)
            whenever(userRepository.create(any())).thenAnswer(CopyUserWithID)

            oauthService.signInThroughOAuth(withPrincipal(testAccessToken))
            verify(userRepository).create(testUserFromOAuth)
        }

    @Test
    fun `given existing user, authenticating with OAuth2, signInThroughOAuth must update user entity with data from OAuth2`() =
        runBlocking {
            val testUsername = "some-username"
            val testAccessToken = "test-access-token"
            val testUserFromOAuth = UserEntity(username = testUsername, role = Role.UNDEFINED.name)
            val testUserFromDatabase = UserEntity(id = 123, username = testUsername, role = Role.USER.name)
            val testUserMustUpdate = UserEntity(id = 123, username = testUsername, role = Role.USER.name)
            val oauthService = OAuthServiceImpl(mockHttpClient(), mockConfig, userRepository, oauthMapper)
            whenever(oauthMapper.mapAccessTokenPayloadToUserEntity(testAccessToken)).thenReturn(testUserFromOAuth)
            whenever(userRepository.findByUsername(testUsername)).thenReturn(testUserFromDatabase)
            whenever(oauthMapper.mergeUserEntities(any(), any())).thenReturn(testUserMustUpdate)

            oauthService.signInThroughOAuth(withPrincipal(testAccessToken))
            verify(userRepository).update(testUserMustUpdate)
        }

    @Test
    fun `given blocked user, authenticating with OAuth2, signInThroughOAuth must fail`(): Unit =
        runBlocking {
            val testUsername = "some-username"
            val testAccessToken = "test-access-token"
            val testUserFromOAuth = UserEntity(username = testUsername, role = Role.USER.name)
            val oauthService = OAuthServiceImpl(mockHttpClient(), mockConfig, userRepository, oauthMapper)
            whenever(oauthMapper.mapAccessTokenPayloadToUserEntity(testAccessToken)).thenReturn(testUserFromOAuth)
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
            val oauthService = OAuthServiceImpl(httpClient, OAuthConfig(config), userRepository, oauthMapper)

            assertThrows<OAuthUnauthorizedException> {
                oauthService.signInThroughOAuth(withPrincipal("invalid-token"))
            }
        }

    @Test
    fun `if userInfoUrl is not specified, signInThroughOAuth must extract user data from access token`(): Unit =
        runBlocking {
            val testUsername = "some-username"
            val testRole = Role.USER
            val testAccessToken = "test-access-token"
            val oauthService = OAuthServiceImpl(mockHttpClient(), mockConfig, userRepository, oauthMapper)
            whenever(oauthMapper.mapAccessTokenPayloadToUserEntity(testAccessToken)).thenReturn(
                UserEntity(username = testUsername, role = testRole.name)
            )
            whenever(userRepository.findByUsername(testUsername)).thenReturn(null)
            whenever(userRepository.create(any())).thenAnswer(CopyUserWithID)

            val userInfo = oauthService.signInThroughOAuth(withPrincipal(testAccessToken))
            verify(oauthMapper).mapAccessTokenPayloadToUserEntity(testAccessToken)
            assertEquals(testUsername, userInfo.username)
            assertEquals(testRole, userInfo.role)
        }

    @Test
    fun `if userInfoUrl is specified, signInThroughOAuth must extract user data from user-info response`(): Unit =
        runBlocking {
            val testUsername = "some-username"
            val testRole = Role.USER
            val testUserInfoResponse = "some-username-with-role-user"
            val config = MapApplicationConfig().apply {
                put("drill.auth.oauth2.userInfoUrl", "http://some-oauth-server.com/userInfoUrl")
            }
            val httpClient = mockHttpClient(
                "/userInfoUrl" shouldRespond {
                    respondOk(testUserInfoResponse)
                }
            )
            val oauthService = OAuthServiceImpl(httpClient, OAuthConfig(config), userRepository, oauthMapper)
            whenever(oauthMapper.mapUserInfoToUserEntity(testUserInfoResponse)).thenReturn(
                UserEntity(username = testUsername, role = testRole.name)
            )
            whenever(userRepository.findByUsername(testUsername)).thenReturn(null)
            whenever(userRepository.create(any())).thenAnswer(CopyUserWithID)

            val userInfo = oauthService.signInThroughOAuth(withPrincipal("test-access-token"))
            verify(oauthMapper).mapUserInfoToUserEntity(testUserInfoResponse)
            assertEquals(testUsername, userInfo.username)
            assertEquals(testRole, userInfo.role)
        }

    @Test
    fun `signInThroughOAuth must merge existing user data and OAuth2 user data and store it in database`(): Unit =
        runBlocking {
            val testUsername = "some-username"
            val testAccessToken = "test-access-token"
            val testUserFromOAuth = UserEntity(username = testUsername, role = Role.USER.name)
            val testUserId = 123
            val testUserFromDatabase = UserEntity(id = testUserId, username = testUsername, role = Role.ADMIN.name)
            val mergedRole = Role.UNDEFINED

            val oauthService = OAuthServiceImpl(mockHttpClient(), mockConfig, userRepository, oauthMapper)
            whenever(oauthMapper.mapAccessTokenPayloadToUserEntity(testAccessToken)).thenReturn(testUserFromOAuth)
            whenever(userRepository.findByUsername(testUsername)).thenReturn(testUserFromDatabase)
            whenever(oauthMapper.mergeUserEntities(testUserFromDatabase, testUserFromOAuth)).thenReturn(
                UserEntity(id = testUserId, username = testUsername, role = mergedRole.name)
            )

            val userInfo = oauthService.signInThroughOAuth(withPrincipal(testAccessToken))
            verify(oauthMapper).mergeUserEntities(testUserFromDatabase, testUserFromOAuth)
            verify(userRepository).update(UserEntity(id = testUserId, username = testUsername, role = mergedRole.name))
            assertEquals(testUsername, userInfo.username)
            assertEquals(mergedRole, userInfo.role)
        }

    private fun withPrincipal(testAccessToken: String) = OAuthAccessTokenResponse.OAuth2(
        accessToken = testAccessToken,
        tokenType = "Bearer",
        expiresIn = 3600,
        refreshToken = null
    )

    object CopyUserWithID: Answer<UserEntity> {
        override fun answer(invocation: InvocationOnMock?) = invocation?.getArgument<UserEntity>(0)?.copy(id = 123)
    }

}

