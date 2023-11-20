package com.epam.drill.admin.auth

import com.epam.drill.admin.auth.config.OAuthConfig
import com.epam.drill.admin.auth.entity.UserEntity
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.repository.UserRepository
import com.epam.drill.admin.auth.service.impl.OAuthServiceImpl
import io.ktor.auth.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.config.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.*

class OAuthServiceTest {
    @Mock
    lateinit var userRepository: UserRepository

    @BeforeTest
    fun setup() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `given new OAuth2 principal, signInThroughOAuth must create new user`() = runBlocking {
        val testUsername = "some-username"
        val testRole = "user"
        val testAccessToken = "test-access-token"

        val httpClient = mockHttpClient(
            "/userInfoUrl" to userInfoResponse(testAccessToken, testUsername, testRole)
        )
        val config = MapApplicationConfig().apply {
            put("drill.auth.oauth2.userInfoUrl", "http://some-oauth-server.com/userInfoUrl")
        }
        val oauthService = OAuthServiceImpl(httpClient, OAuthConfig(config), userRepository)
        whenever(userRepository.findByUsername(testUsername)).thenReturn(null)
        whenever(userRepository.create(any())).thenReturn(1)

        val principal = OAuthAccessTokenResponse.OAuth2(
            accessToken = testAccessToken,
            tokenType = "Bearer",
            expiresIn = 3600,
            refreshToken = null
        )
        val userInfo = oauthService.signInThroughOAuth(principal)
        verify(userRepository).create(any())
        assertEquals(testUsername, userInfo.username)
        assertTrue(testRole.equals(userInfo.role.name, true))
    }

    @Test
    fun `given OAuth2 principal that was already logged in, signInThroughOAuth must update user`() = runBlocking {
        val testUsername = "some-username"
        val testRole = "user"
        val testAccessToken = "test-access-token"

        val httpClient = mockHttpClient(
            "/userInfoUrl" to userInfoResponse(testAccessToken, testUsername, testRole)
        )
        val config = MapApplicationConfig().apply {
            put("drill.auth.oauth2.userInfoUrl", "http://some-oauth-server.com/userInfoUrl")
        }
        val oauthService = OAuthServiceImpl(httpClient, OAuthConfig(config), userRepository)
        whenever(userRepository.findByUsername(testUsername)).thenReturn(UserEntity(
            id = 1,
            username = testUsername,
            role = Role.UNDEFINED.name
        ))

        val principal = OAuthAccessTokenResponse.OAuth2(
            accessToken = testAccessToken,
            tokenType = "Bearer",
            expiresIn = 3600,
            refreshToken = null
        )
        val userInfo = oauthService.signInThroughOAuth(principal)
        verify(userRepository).update(any())
        assertEquals(testUsername, userInfo.username)
        assertTrue(testRole.equals(userInfo.role.name, true))
    }

    private fun userInfoResponse(
        testAccessToken: String,
        testUsername: String,
        testRole: String
    ): suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData =
        { request ->
            assertEquals("Bearer $testAccessToken", request.headers[HttpHeaders.Authorization])
            respondOk(
                """
                                {                              
                                  "preferred_username":"$testUsername",
                                  "roles":["$testRole"]                             
                                }     
                                """.trimIndent()
            )
        }

}

