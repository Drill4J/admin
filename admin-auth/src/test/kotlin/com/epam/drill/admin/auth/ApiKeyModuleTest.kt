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

import com.epam.drill.admin.auth.config.*
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.repository.ApiKeyRepository
import com.epam.drill.admin.auth.route.simpleAuthStatusPages
import com.epam.drill.admin.auth.service.ApiKeyService
import com.epam.drill.admin.auth.service.PasswordService
import com.epam.drill.admin.auth.service.impl.ApiKeyServiceImpl
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.testing.*
import org.kodein.di.*
import org.kodein.di.ktor.closestDI
import org.kodein.di.ktor.di
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.*

/**
 * Tests for [ApiKeyService.signInThroughApiKey] and [apiKeyDIModule]
 */
class ApiKeyModuleTest {

    @Mock
    private lateinit var mockApiKeyRepository: ApiKeyRepository

    @Mock
    private lateinit var mockPasswordService: PasswordService

    @BeforeTest
    fun setup() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `given valid api key, api-key authenticated request must succeed`() {
        val validApiKey = "valid-api-key"
        wheneverBlocking(mockApiKeyRepository) { getAll() }
            .doReturn(listOf(createTestApiKeyEntity()))
        whenever(mockPasswordService.matchPasswords(eq(validApiKey), any())).doReturn(true)

        withTestApplication({
            withApiKeyModule {
                configureMocks(mockApiKeyRepository, mockPasswordService)
            }
            routing {
                authenticate("api-key") {
                    configureGetApiRoute()
                }
            }
        }) {
            with(handleRequest(HttpMethod.Get, "/api") {
                addHeader(API_KEY_HEADER, validApiKey)
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    @Test
    fun `without api key, api-key authenticated request must fail with 401 status`() {
        withTestApplication({
            withApiKeyModule {
                configureMocks(mockApiKeyRepository, mockPasswordService)
            }
            routing {
                authenticate("api-key") {
                    configureGetApiRoute()
                }
            }
        }) {
            with(handleRequest(HttpMethod.Get, "/api") {
                //no adding API key header
            }) {
                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
        }
    }

    @Test
    fun `given invalid api key, api-key authenticated request must fail with 401 status`() {
        val invalidApiKey = "invalid-api-key"
        wheneverBlocking(mockApiKeyRepository) { getAll() }
            .doReturn(listOf(createTestApiKeyEntity(apiKeyHash = "hash")))
        whenever(mockPasswordService.matchPasswords(invalidApiKey, "hash")).doReturn(false)

        withTestApplication({
            withApiKeyModule {
                configureMocks(mockApiKeyRepository, mockPasswordService)
            }
            routing {
                authenticate("api-key") {
                    configureGetApiRoute()
                }
            }
        }) {
            with(handleRequest(HttpMethod.Get, "/api") {
                addHeader(API_KEY_HEADER, invalidApiKey)
            }) {
                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
        }
    }

    @Test
    fun `given expired api key, api-key authenticated request must fail with 401 status`() {
        val expiredApiKey = "expired-api-key"
        wheneverBlocking(mockApiKeyRepository) { getAll() }
            .doReturn(listOf(createTestApiKeyEntity(expiresAt = LocalDateTime.now().minusDays(1))))
        whenever(mockPasswordService.matchPasswords(eq(expiredApiKey), any())).doReturn(true)

        withTestApplication({
            withApiKeyModule {
                configureMocks(mockApiKeyRepository, mockPasswordService)
            }
            routing {
                authenticate("api-key") {
                    configureGetApiRoute()
                }
            }
        }) {
            with(handleRequest(HttpMethod.Get, "/api") {
                addHeader(API_KEY_HEADER, expiredApiKey)
            }) {
                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
        }
    }

    @Test
    fun `given blocked api key, api-key authenticated request must fail with 403 status`() {
        val blockedApiKey = "blocked-api-key"
        wheneverBlocking(mockApiKeyRepository) { getAll() }
            .doReturn(listOf(createTestApiKeyEntity(user = createTestUserEntity(blocked = true))))
        whenever(mockPasswordService.matchPasswords(eq(blockedApiKey), any())).doReturn(true)

        withTestApplication({
            withApiKeyModule {
                configureMocks(mockApiKeyRepository, mockPasswordService)
            }
            routing {
                authenticate("api-key") {
                    configureGetApiRoute()
                }
            }
        }) {
            with(handleRequest(HttpMethod.Get, "/api") {
                addHeader(API_KEY_HEADER, blockedApiKey)
            }) {
                assertEquals(HttpStatusCode.Forbidden, response.status())
            }
        }
    }

    @Test
    fun `given undefined role if api key, api-key authenticated request must fail with 403 status`() {
        val undefinedRoleApiKey = "undefined-role-api-key"
        wheneverBlocking(mockApiKeyRepository) { getAll() }
            .doReturn(listOf(createTestApiKeyEntity(user = createTestUserEntity(role = Role.UNDEFINED))))
        whenever(mockPasswordService.matchPasswords(eq(undefinedRoleApiKey), any())).doReturn(true)

        withTestApplication({
            withApiKeyModule {
                configureMocks(mockApiKeyRepository, mockPasswordService)
            }
            routing {
                authenticate("api-key") {
                    configureGetApiRoute()
                }
            }
        }) {
            with(handleRequest(HttpMethod.Get, "/api") {
                addHeader(API_KEY_HEADER, undefinedRoleApiKey)
            }) {
                assertEquals(HttpStatusCode.Forbidden, response.status())
            }
        }
    }


    private fun Route.configureGetApiRoute() {
        get("/api") {
            call.respond(HttpStatusCode.OK)
        }
    }

    private fun Application.withApiKeyModule(configureDI: DI.MainBuilder.() -> Unit = {}) {
        install(ContentNegotiation) {
            json()
        }
        install(StatusPages) {
            simpleAuthStatusPages()
        }

        di {
            import(apiKeyServicesDIModule)
            configureDI()
        }

        install(Authentication) {
            configureApiKeyAuthentication(closestDI())
        }
    }

    private fun DI.MainBuilder.configureMocks(
        mockApiKeyRepository: ApiKeyRepository,
        mockPasswordService: PasswordService
    ) {
        bind<PasswordService>(overrides = true) with provider { mockPasswordService }
        bind<ApiKeyRepository>(overrides = true) with provider { mockApiKeyRepository }
        bind<ApiKeyService>(overrides = true) with singleton {
            ApiKeyServiceImpl(
                repository = instance(),
                passwordService = instance(),
            )
        }
    }
}
