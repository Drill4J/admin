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

import com.epam.drill.admin.auth.model.ApiKeyView
import com.epam.drill.admin.auth.repository.ApiKeyRepository
import com.epam.drill.admin.auth.route.*
import com.epam.drill.admin.auth.service.ApiKeyBuilder
import com.epam.drill.admin.auth.service.ApiKeyService
import com.epam.drill.admin.auth.service.PasswordService
import com.epam.drill.admin.auth.service.SecretGenerator
import com.epam.drill.admin.auth.service.impl.ApiKeyServiceImpl
import io.ktor.server.application.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.resources.*
import io.ktor.server.testing.*
import kotlinx.serialization.builtins.ListSerializer
import io.ktor.client.request.*
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

    @Mock
    lateinit var apiKeyBuilder: ApiKeyBuilder

    @Mock
    lateinit var secretGenerator: SecretGenerator

    @BeforeTest
    fun setup() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `'GET keys' must return the expected number of api keys from repository`() {
        wheneverBlocking(apiKeyRepository) { findAll() }.thenReturn(
            listOf(
                createTestApiKeyEntity(id = 1),
                createTestApiKeyEntity(id = 2),
            )
        )

        testApplication {
            application(withRoute { getAllApiKeysRoute() })
            val response = client.get("/keys") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }
            assertEquals(HttpStatusCode.OK, response.status)
            val body: List<ApiKeyView> = response.assertResponseNotNull(ListSerializer(ApiKeyView.serializer()))
            assertEquals(2, body.size)
        }
    }

    @Test
    fun `given api key identifier, 'DELETE keys {id}' must delete that api key from repository`() {
        testApplication {
            application(withRoute { deleteApiKeyRoute() })
            val response = client.delete("/keys/1") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }
            assertEquals(HttpStatusCode.OK, response.status)
            verifyBlocking(apiKeyRepository) { deleteById(1) }
        }
    }

    private fun withRoute(route: Route.() -> Unit): Application.() -> Unit = {
        install(Resources)
        install(ContentNegotiation) {
            json()
        }
        di {
            bind<ApiKeyService>() with provider {
                ApiKeyServiceImpl(
                    repository = apiKeyRepository,
                    secretService = passwordService,
                    apiKeyBuilder = apiKeyBuilder,
                    secretGenerator = secretGenerator,
                )
            }
        }
        install(StatusPages) {
            authStatusPages()
        }
        routing {
            route()
        }
    }

}