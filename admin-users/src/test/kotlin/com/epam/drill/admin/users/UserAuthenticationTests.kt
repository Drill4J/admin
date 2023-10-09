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
package com.epam.drill.admin.users

import com.epam.drill.admin.users.view.ChangePasswordForm
import com.epam.drill.admin.users.view.TokenResponse
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.*

class SignInTest {

    @Test
    fun `given username and password 'guest' sign-in results should have token`() {
        withTestApplication(testApp) {
            with(handleRequest(HttpMethod.Post, "/sign-in"){
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("{\"username\":\"guest\", \"password\":\"guest\"}")
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
                val response = Json.decodeFromString(TokenResponse.serializer(), response.content!!)
                assertEquals("token", response.token)
            }
        }
    }
}

class SignUpTest {

    @Test
    fun `given username and password 'guest' sign-up results should return OK status`() {
        withTestApplication(testApp) {
            with(handleRequest(HttpMethod.Post, "/sign-up"){
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("{\"username\":\"guest\", \"password\":\"guest\"}")
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }
}

class ChangePasswordTest {

    @Test
    fun `given correct old password update-password results should return OK status`() {
        withTestApplication(testApp) {
            with(handleRequest(HttpMethod.Post, "/update-password"){
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                val form = ChangePasswordForm(oldPassword = "password1", newPassword = "password2")
                setBody(Json.encodeToString(ChangePasswordForm.serializer(), form))
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }
}