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
import com.epam.drill.admin.auth.model.Role
import com.epam.drill.admin.auth.repository.UserRepository
import com.epam.drill.admin.auth.route.UserAuthenticationRoutes
import com.epam.drill.admin.auth.service.PasswordService
import com.epam.drill.admin.auth.service.TokenService
import com.epam.drill.admin.auth.service.UserAuthenticationService
import com.epam.drill.admin.auth.service.impl.UserAuthenticationServiceImpl
import com.epam.drill.admin.auth.view.ChangePasswordForm
import com.epam.drill.admin.auth.view.LoginForm
import com.epam.drill.admin.auth.view.RegistrationForm
import com.epam.drill.admin.auth.view.TokenResponse
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
    fun `given username and password 'guest' sign-in results should have token`() {
        whenever(userRepository.findByUsername("guest"))
            .thenReturn(userGuest.copy())
        whenever(passwordService.checkPassword("secret", "hash"))
            .thenReturn(true)
        whenever(tokenService.issueToken(any())).thenReturn("token")

        withTestApplication(config()) {
            with(handleRequest(HttpMethod.Post, "/sign-in") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                val form = LoginForm(username = "guest", password = "secret")
                setBody(Json.encodeToString(LoginForm.serializer(), form))
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
                val response = Json.decodeFromString(TokenResponse.serializer(), response.content!!)
                assertEquals("token", response.token)
            }
        }
    }

    @Test
    fun `given username and password 'guest' sign-up results should return OK status`() {
        whenever(userRepository.findByUsername("guest"))
            .thenReturn(null)
        whenever(passwordService.hashPassword("secret"))
            .thenReturn("hash")
        whenever(userRepository.create(any()))
            .thenReturn(1)

        withTestApplication(config()) {
            with(handleRequest(HttpMethod.Post, "/sign-up") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                val form = RegistrationForm(username = "guest", password = "secret")
                setBody(Json.encodeToString(RegistrationForm.serializer(), form))
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
    fun `given correct old password update-password results should return OK status`() {
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
                val form = ChangePasswordForm(oldPassword = "secret", newPassword = "secret2")
                setBody(Json.encodeToString(ChangePasswordForm.serializer(), form))
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
                verify(userRepository).update(userGuest.copy(passwordHash = "hash2"))
            }
        }
    }

    private fun config() = testApp {
        bind<UserRepository>() with eagerSingleton { userRepository }
        bind<PasswordService>() with eagerSingleton { passwordService }
        bind<TokenService>() with eagerSingleton { tokenService }
        bind<UserAuthenticationService>() with eagerSingleton {
            UserAuthenticationServiceImpl(
                instance(),
                instance()
            )
        }
        bind<UserAuthenticationRoutes>() with eagerSingleton { UserAuthenticationRoutes(di) }
    }

    private fun TestApplicationRequest.addBasicAuth(username: String, password: String) {
        val encodedCredentials = String(Base64.getEncoder().encode("$username:$password".toByteArray()))
        addHeader(HttpHeaders.Authorization, "Basic $encodedCredentials")
    }
}
