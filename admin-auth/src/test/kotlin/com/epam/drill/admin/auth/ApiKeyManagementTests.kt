package com.epam.drill.admin.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.epam.drill.admin.auth.config.CLAIM_ROLE
import com.epam.drill.admin.auth.config.CLAIM_USER_ID
import com.epam.drill.admin.auth.entity.UserEntity
import com.epam.drill.admin.auth.model.ApiKeyView
import com.epam.drill.admin.auth.model.EditUserPayload
import com.epam.drill.admin.auth.model.UserView
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.principal.User
import com.epam.drill.admin.auth.repository.UserRepository
import com.epam.drill.admin.auth.route.*
import com.epam.drill.admin.auth.service.ApiKeyService
import com.epam.drill.admin.auth.service.PasswordService
import com.epam.drill.admin.auth.service.UserManagementService
import com.epam.drill.admin.auth.service.impl.UserManagementServiceImpl
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
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
import org.mockito.kotlin.any
import org.mockito.kotlin.verifyBlocking

/**
 * Testing /keys routers
 */
class ApiKeyManagementTests {
    @Mock
    lateinit var apiKeyService: ApiKeyService

    @BeforeTest
    fun setup() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `'GET keys' must return the expected number of api keys from repository`() {
        wheneverBlocking(apiKeyService) { getAllApiKeys() }.thenReturn(
            listOf(
                ApiKeyView(
                    id = 1,
                    userId = 1,
                    username = "user1",
                    description = "key1",
                    role = Role.USER,
                    expired = LocalDateTime.parse("2025-12-31T00:00:00"),
                    created = LocalDateTime.parse("2023-01-01T00:00:00")),
                ApiKeyView(
                    id = 2,
                    userId = 2,
                    username = "user2",
                    description = "key2",
                    role = Role.ADMIN,
                    expired = LocalDateTime.parse("2025-12-31T00:00:00"),
                    created = LocalDateTime.parse("2023-01-01T00:00:00")),
            )
        )

        withTestApplication(withRoute { getAllApiKeysRoute() }) {
            with(handleRequest(HttpMethod.Get, "/keys") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
                val response: List<ApiKeyView> = assertResponseNotNull(ListSerializer(ApiKeyView.serializer()))
                assertEquals(2, response.size)
            }
        }
    }

    @Test
    fun `given api key identifier 'DELETE keys {id}' must delete that api key in repository`() {
        withTestApplication(withRoute { deleteApiKeyRoute() }) {
            with(handleRequest(HttpMethod.Delete, "/keys/1") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
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
        install(StatusPages) {
            simpleAuthStatusPages()
        }
        routing {
            route()
        }
    }

}