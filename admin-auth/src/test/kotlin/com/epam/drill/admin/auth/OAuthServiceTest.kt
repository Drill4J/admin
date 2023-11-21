package com.epam.drill.admin.auth

import com.epam.drill.admin.auth.config.OAuthConfig
import com.epam.drill.admin.auth.config.OAuthUnauthorizedException
import com.epam.drill.admin.auth.entity.UserEntity
import com.epam.drill.admin.auth.exception.NotAuthorizedException
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.repository.UserRepository
import com.epam.drill.admin.auth.service.impl.OAuthServiceImpl
import io.ktor.auth.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
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
    private val config = MapApplicationConfig().apply {
        put("drill.auth.oauth2.userInfoUrl", "http://some-oauth-server.com/userInfoUrl")
    }

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
        val oauthService = OAuthServiceImpl(httpClient, OAuthConfig(config), userRepository)
        whenever(userRepository.findByUsername(testUsername)).thenReturn(null)
        whenever(userRepository.create(any())).thenReturn(1)

        val userInfo = oauthService.signInThroughOAuth(withPrincipal(testAccessToken))
        verify(userRepository).create(any())
        assertEquals(testUsername, userInfo.username)
        assertTrue(testRole.equals(userInfo.role.name, true))
    }

    @Test
    fun `given OAuth2 principal that was already logged in, signInThroughOAuth must update user role`() = runBlocking {
        val testUsername = "some-username"
        val testRole = "nonexistent-role"
        val testAccessToken = "test-access-token"

        val httpClient = mockHttpClient(
            "/userInfoUrl" to userInfoResponse(testAccessToken, testUsername, testRole)
        )
        val oauthService = OAuthServiceImpl(httpClient, OAuthConfig(config), userRepository)
        whenever(userRepository.findByUsername(testUsername)).thenReturn(UserEntity(
            id = 1,
            username = testUsername,
            role = Role.USER.name
        ))

        val userInfo = oauthService.signInThroughOAuth(withPrincipal(testAccessToken))
        verify(userRepository).update(any())
        assertEquals(testUsername, userInfo.username)
        assertEquals(Role.USER, userInfo.role)
    }

    @Test
    fun `given blocked OAuth2 principal, signInThroughOAuth must failed`(): Unit = runBlocking {
        val testUsername = "some-username"
        val testRole = "user"
        val testAccessToken = "test-access-token"

        val httpClient = mockHttpClient(
            "/userInfoUrl" to userInfoResponse(testAccessToken, testUsername, testRole)
        )
        val oauthService = OAuthServiceImpl(httpClient, OAuthConfig(config), userRepository)
        whenever(userRepository.findByUsername(testUsername)).thenReturn(UserEntity(
            id = 1,
            username = testUsername,
            role = Role.USER.name,
            blocked = true
        ))

        val principal = withPrincipal(testAccessToken)
        assertThrows<OAuthUnauthorizedException> {
            oauthService.signInThroughOAuth(principal)
        }
    }

    @Test
    fun `given invalid accessToken, signInThroughOAuth must failed`(): Unit = runBlocking {
        val testUsername = "some-username"
        val testAccessToken = "invalid-token"

        val httpClient = mockHttpClient(
            "/userInfoUrl" to { _ ->
                respondError(HttpStatusCode.Unauthorized, "Invalid token")
            }
        )
        val oauthService = OAuthServiceImpl(httpClient, OAuthConfig(config), userRepository)
        whenever(userRepository.findByUsername(testUsername)).thenReturn(UserEntity(
            id = 1,
            username = testUsername,
            role = Role.USER.name,
            blocked = true
        ))

        val principal = withPrincipal(testAccessToken)
        assertThrows<OAuthUnauthorizedException> {
            oauthService.signInThroughOAuth(principal)
        }
    }

    private fun withPrincipal(testAccessToken: String) = OAuthAccessTokenResponse.OAuth2(
        accessToken = testAccessToken,
        tokenType = "Bearer",
        expiresIn = 3600,
        refreshToken = null
    )

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

