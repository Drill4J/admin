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

import com.epam.drill.admin.auth.entity.UserEntity
import com.epam.drill.admin.auth.entity.Role
import com.epam.drill.admin.auth.repository.UserRepository
import com.epam.drill.admin.auth.route.userAuthenticationRoutes
import com.epam.drill.admin.auth.service.PasswordService
import com.epam.drill.admin.auth.service.TokenService
import com.epam.drill.admin.auth.service.UserAuthenticationService
import com.epam.drill.admin.auth.service.impl.UserAuthenticationServiceImpl
import com.epam.drill.admin.auth.view.ChangePasswordPayload
import com.epam.drill.admin.auth.view.LoginPayload
import com.epam.drill.admin.auth.view.RegistrationPayload
import com.epam.drill.admin.auth.view.TokenView
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.kodein.di.bind
import org.kodein.di.eagerSingleton
import org.kodein.di.instance
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Base64
import kotlin.test.*

class UserAuthenticationTest {

    @Mock
    lateinit var userRepository: UserRepository

    @Mock
    lateinit var passwordService: PasswordService

    @Mock
    lateinit var tokenService: TokenService

    @BeforeTest
    fun setup() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `given expected username and password, sign-in service should return an access token`() {
        whenever(userRepository.findByUsername("guest"))
            .thenReturn(userGuest.copy())
        whenever(passwordService.checkPassword("secret", "hash"))
            .thenReturn(true)
        whenever(tokenService.issueToken(any())).thenReturn("token")

        withTestApplication(config()) {
            with(handleRequest(HttpMethod.Post, "/sign-in") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                val payload = LoginPayload(username = "guest", password = "secret")
                setBody(Json.encodeToString(LoginPayload.serializer(), payload))
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
                val response = Json.decodeFromString(TokenView.serializer(), assertNotNull(response.content))
                assertEquals("token", response.token)
            }
        }
    }

    @Test
    fun `given unique username and correct password, sign-up service must create a user`() {
        whenever(userRepository.findByUsername("guest"))
            .thenReturn(null)
        whenever(passwordService.hashPassword("secret"))
            .thenReturn("hash")
        whenever(userRepository.create(any()))
            .thenReturn(1)

        withTestApplication(config()) {
            with(handleRequest(HttpMethod.Post, "/sign-up") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                val form = RegistrationPayload(username = "guest", password = "secret")
                setBody(Json.encodeToString(RegistrationPayload.serializer(), form))
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
                verify(userRepository).create(
                    UserEntity(
                        username = "guest",
                        passwordHash = "hash",
                        role = Role.UNDEFINED.name
                    )
                )
            }
        }
    }

    @Test
    fun `given correct old password, update-password service must update the password to the new one`() {
        whenever(userRepository.findByUsername("guest"))
            .thenReturn(userGuest.copy())
        whenever(passwordService.checkPassword("secret", "hash"))
            .thenReturn(true)
        whenever(passwordService.hashPassword("secret2"))
            .thenReturn("hash2")

        withTestApplication(config()) {
            with(handleRequest(HttpMethod.Post, "/update-password") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                addBasicAuth("guest", "secret")
                val form = ChangePasswordPayload(oldPassword = "secret", newPassword = "secret2")
                setBody(Json.encodeToString(ChangePasswordPayload.serializer(), form))
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
                verify(userRepository).update(userGuest.copy(passwordHash = "hash2"))
            }
        }
    }

    private fun config() = testApp(
        authentication = {
            //have to use "jwt" for basic auth, because that name is required for this route in production code
            basic("jwt") {
                validate {
                    UserIdPrincipal(it.name)
                }
            }
        },
        routing = {
            userAuthenticationRoutes()
        },
        bindings = {
            bind<UserRepository>() with eagerSingleton { userRepository }
            bind<PasswordService>() with eagerSingleton { passwordService }
            bind<TokenService>() with eagerSingleton { tokenService }
            bind<UserAuthenticationService>() with eagerSingleton {
                UserAuthenticationServiceImpl(
                    instance(),
                    instance()
                )
            }
        }
    )

    private fun TestApplicationRequest.addBasicAuth(username: String, password: String) {
        val encodedCredentials = String(Base64.getEncoder().encode("$username:$password".toByteArray()))
        addHeader(HttpHeaders.Authorization, "Basic $encodedCredentials")
    }
}
