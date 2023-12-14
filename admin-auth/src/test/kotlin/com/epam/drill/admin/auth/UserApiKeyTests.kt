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
import kotlinx.datetime.toKotlinLocalDateTime
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
import java.time.Month


/**
 * Testing /user-keys routers
 */
class UserApiKeyTests {
    @Mock
    lateinit var apiKeyRepository: ApiKeyRepository

    @Mock
    lateinit var passwordService: PasswordService

    @Mock
    lateinit var currentTimeProvider: CurrentTimeProvider

    val firstJanuary2023 = LocalDateTime.of(2023, Month.JANUARY, 1, 0, 0)

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
        wheneverBlocking(currentTimeProvider) { getCurrentTime() }.thenReturn(firstJanuary2023)
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
    fun `given three month expiry period, 'POST user-keys' must return api key with expiration date in three month from current date`() {
        wheneverBlocking(passwordService) { hashPassword(any()) }.thenReturn("hash")
        wheneverBlocking(currentTimeProvider) { getCurrentTime() }.thenReturn(firstJanuary2023)
        wheneverBlocking(apiKeyRepository) { create(any()) }.thenAnswer(copyApiKeyWithId(123))

        withTestApplication(withRoute {
            authenticate {
                generateUserApiKeyRoute()
            }
        }) {
            with(handleRequest(HttpMethod.Post, "/user-keys") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                val form = GenerateApiKeyPayload(
                    description = "for some client",
                    expiryPeriod = ExpiryPeriod.THREE_MONTHS
                )
                setBody(Json.encodeToString(GenerateApiKeyPayload.serializer(), form))
                addJwtToken(username = "test-user", userId = 43)
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
                val response: ApiKeyCredentialsView = assertResponseNotNull(ApiKeyCredentialsView.serializer())
                assertEquals(firstJanuary2023.plusMonths(3).toKotlinLocalDateTime(), response.expiresAt)
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
            bind<ApiKeyService>() with provider {
                ApiKeyServiceImpl(
                    repository = apiKeyRepository,
                    passwordService = passwordService,
                    currentDateTimeProvider = { currentTimeProvider.getCurrentTime() }
                )
            }
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

interface CurrentTimeProvider {
    fun getCurrentTime(): LocalDateTime
}