package com.epam.drill.admin.auth

import com.epam.drill.admin.auth.model.ApiKeyView
import com.epam.drill.admin.auth.repository.ApiKeyRepository
import com.epam.drill.admin.auth.route.*
import com.epam.drill.admin.auth.service.ApiKeyService
import com.epam.drill.admin.auth.service.PasswordService
import com.epam.drill.admin.auth.service.impl.ApiKeyServiceImpl
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.testing.*
import kotlinx.serialization.builtins.ListSerializer
import org.kodein.di.bind
import org.kodein.di.ktor.di
import org.kodein.di.provider
import kotlin.test.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verifyBlocking

/**
 * Testing /keys routers
 */
class ApiKeyManagementTests {
    @Mock
    lateinit var apiKeyRepository: ApiKeyRepository

    @Mock
    lateinit var passwordService: PasswordService

    @BeforeTest
    fun setup() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `'GET keys' must return the expected number of api keys from repository`() {
        wheneverBlocking(apiKeyRepository) { getAll() }.thenReturn(
            listOf(
                createTestApiKeyEntity(id = 1),
                createTestApiKeyEntity(id = 2),
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
    fun `given api key identifier, 'DELETE keys {id}' must delete that api key from repository`() {
        withTestApplication(withRoute { deleteApiKeyRoute() }) {
            with(handleRequest(HttpMethod.Delete, "/keys/1") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
                verifyBlocking(apiKeyRepository) { deleteById(1) }
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
        install(StatusPages) {
            simpleAuthStatusPages()
        }
        routing {
            route()
        }
    }

}