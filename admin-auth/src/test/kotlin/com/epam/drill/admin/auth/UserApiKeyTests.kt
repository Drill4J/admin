package com.epam.drill.admin.auth

import com.epam.drill.admin.auth.model.*
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.route.*
import com.epam.drill.admin.auth.service.ApiKeyService
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.testing.*
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.kodein.di.bind
import org.kodein.di.ktor.di
import org.kodein.di.provider
import kotlin.test.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations

/**
 * Testing /user-keys routers
 */
class UserApiKeyTests {
    @Mock
    lateinit var apiKeyService: ApiKeyService

    @BeforeTest
    fun setup() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `'GET user-keys' must return the expected number of user's api keys`() {
        val testUserId = 43
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
                val response: List<ApiKeyView> = assertResponseNotNull(ListSerializer(ApiKeyView.serializer()))
                assertEquals(2, response.size)
            }
        }
    }

    @Test
    fun `given api key identifier 'POST user-keys' must return generated api key`() {
        val testUserId = 43
        withTestApplication(withRoute {
            authenticate {
                generateUserApiKeyRoute()
            }
        }) {
            with(handleRequest(HttpMethod.Post, "/user-keys") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                val form = GenerateApiKeyPayload(
                    description = "for some client",
                    expiryPeriod = ExpiryPeriod.ONE_MONTH)
                setBody(Json.encodeToString(GenerateApiKeyPayload.serializer(), form))
                addJwtToken(username = "test-user", userId = testUserId)
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
                val response: ApiKeyCredentialsView = assertResponseNotNull(ApiKeyCredentialsView.serializer())
                assertNotNull(response.id)
                assertNotNull(response.apiKey)
                assertNotNull(response.expired)
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
            }
        }
    }

    private fun withRoute(route: Routing.() -> Unit): Application.() -> Unit = {
        install(Locations)
        install(ContentNegotiation) {
            json()
        }
        di {
            bind<ApiKeyService>() with provider { apiKeyService }
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
}