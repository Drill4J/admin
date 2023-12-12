package com.epam.drill.admin.auth

import com.epam.drill.admin.auth.entity.ApiKeyEntity
import com.epam.drill.admin.auth.entity.UserEntity
import com.epam.drill.admin.auth.model.*
import com.epam.drill.admin.auth.repository.ApiKeyRepository
import com.epam.drill.admin.auth.route.*
import com.epam.drill.admin.auth.service.ApiKeyService
import com.epam.drill.admin.auth.service.PasswordService
import com.epam.drill.admin.auth.service.impl.ApiKeyServiceImpl
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.testing.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.kodein.di.bind
import org.kodein.di.ktor.di
import org.kodein.di.provider
import kotlin.test.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.verifyBlocking
import java.time.LocalDateTime

/**
 * Testing /user-keys routers
 */
class UserApiKeyTests {
    @Mock
    lateinit var apiKeyRepository: ApiKeyRepository

    @Mock
    lateinit var passwordService: PasswordService

    @BeforeTest
    fun setup() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `'GET user-keys' must return the expected number of user's api keys`() {
        val testUserId = 43
        wheneverBlocking(apiKeyRepository) { getAllByUserId(testUserId) }.thenReturn(
            listOf(
                createTestApiKeyEntity(id = 1, userId = testUserId, user = null),
                createTestApiKeyEntity(id = 2, userId = testUserId, user = null),
            )
        )

        withTestApplication(withRoute {
            authenticate {
                getUserApiKeysRoute()
            }
        }) {
            with(handleRequest(HttpMethod.Get, "/user-keys") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                addJwtToken(username = "test-user", userId = testUserId)
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
                val response: List<UserApiKeyView> = assertResponseNotNull(ListSerializer(UserApiKeyView.serializer()))
                assertEquals(2, response.size)
            }
        }
    }

    @Test
    fun `given api key payload, 'POST user-keys' must return generated api key`() {
        val testUserId = 43
        val testApiKeyId = 123
        wheneverBlocking(passwordService) { hashPassword(any()) }.thenReturn("hash")
        wheneverBlocking(apiKeyRepository) { create(any()) }.thenAnswer(copyApiKeyWithId(testApiKeyId))

        withTestApplication(withRoute {
            authenticate {
                generateUserApiKeyRoute()
            }
        }) {
            with(handleRequest(HttpMethod.Post, "/user-keys") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                val form = GenerateApiKeyPayload(
                    description = "for some client",
                    expiryPeriod = ExpiryPeriod.ONE_MONTH
                )
                setBody(Json.encodeToString(GenerateApiKeyPayload.serializer(), form))
                addJwtToken(username = "test-user", userId = testUserId)
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
                val response: ApiKeyCredentialsView = assertResponseNotNull(ApiKeyCredentialsView.serializer())
                assertEquals(testApiKeyId, response.id)
                assertNotNull(response.apiKey)
                assertNotNull(response.expiresAt)
            }
        }
    }

    @Test
    fun `given api key identifier 'DELETE user-keys {id}' must delete user's api key`() {
        val testUserId = 43
        val testApiKey = 123
        withTestApplication(withRoute {
            authenticate {
                deleteUserApiKeyRoute()
            }
        }) {
            with(handleRequest(HttpMethod.Delete, "/user-keys/$testApiKey") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                addJwtToken(username = "test-user", userId = testUserId)
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
                verifyBlocking(apiKeyRepository) { deleteById(testApiKey) }
            }
        }
    }

    private fun withRoute(route: Routing.() -> Unit): Application.() -> Unit = {
        install(Locations)
        install(ContentNegotiation) {
            json()
        }
        di {
            bind<ApiKeyService>() with provider { ApiKeyServiceImpl(apiKeyRepository, passwordService) }
        }
        install(Authentication) {
            jwtMock()
        }
        install(StatusPages) {
            simpleAuthStatusPages()
        }
        routing {
            route()
        }
    }

    private fun createTestApiKeyEntity(
        id: Int? = null,
        userId: Int = 101,
        description: String = "for testing",
        apiKeyHash: String = "hash$id",
        expiresAt: LocalDateTime = LocalDateTime.now().plusYears(1),
        createdAt: LocalDateTime = LocalDateTime.now(),
        user: UserEntity? = null
    ) = ApiKeyEntity(
        id = id,
        userId = userId,
        description = description,
        apiKeyHash = apiKeyHash,
        expiresAt = expiresAt,
        createdAt = createdAt,
        user = user
    )
}