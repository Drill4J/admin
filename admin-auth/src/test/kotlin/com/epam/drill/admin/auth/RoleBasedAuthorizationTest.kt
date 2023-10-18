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

import com.epam.drill.admin.auth.config.RoleBasedAuthorization
import com.epam.drill.admin.auth.config.withRole
import com.epam.drill.admin.auth.exception.NotAuthorizedException
import com.epam.drill.admin.auth.entity.Role
import com.epam.drill.admin.auth.entity.Role.ADMIN
import com.epam.drill.admin.auth.entity.Role.USER
import com.epam.drill.admin.auth.principal.User
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class RoleBasedAuthorizationTest {

    @Test
    fun `given user with admin role, request only-admins should return 200 OK`() {
        withTestApplication(config()) {
            withStatusPages()
            with(handleRequest(HttpMethod.Get, "/only-admins") {
                addBasicAuth("admin", "secret")
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    @Test
    fun `given user with user role, request only-admins should return 403 Access denied`() {
        withTestApplication(config()) {
            withStatusPages()
            with(handleRequest(HttpMethod.Get, "/only-admins") {
                addBasicAuth("user", "secret")
            }) {
                assertEquals(HttpStatusCode.Forbidden, response.status())
            }
        }
    }

    @Test
    fun `given user with user role, request only-users should return 200 OK`() {
        withTestApplication(config()) {
            withStatusPages()
            with(handleRequest(HttpMethod.Get, "/only-users") {
                addBasicAuth("user", "secret")
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    @Test
    fun `given user with user role, request admins-or-users should return 200 OK`() {
        withTestApplication(config()) {
            withStatusPages()
            with(handleRequest(HttpMethod.Get, "/admins-or-users") {
                addBasicAuth("user", "secret")
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    @Test
    fun `given guest without role, request admins-or-users should return 401 Access denied`() {
        withTestApplication(config()) {
            withStatusPages()
            with(handleRequest(HttpMethod.Get, "/admins-or-users") {
                addBasicAuth("guest", "secret")
            }) {
                assertEquals(HttpStatusCode.Forbidden, response.status())
            }
        }
    }

    @Test
    fun `given guest without role, request all should return 200 OK`() {
        withTestApplication(config()) {
            withStatusPages()
            with(handleRequest(HttpMethod.Get, "/all") {
                addBasicAuth("guest", "secret")
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    private fun config() = testModule(
        statusPages = {
            exception<NotAuthorizedException> { _ ->
                call.respond(HttpStatusCode.Forbidden, "Access denied")
            }
        },
        features = {
            install(RoleBasedAuthorization)
        },
        authentication = {
            basic {
                validate {
                    when (it.name) {
                        "user" -> User(it.name, USER)
                        "admin" -> User(it.name, ADMIN)
                        else -> User(it.name, Role.UNDEFINED)
                    }
                }
            }
        },
        routing = {
            authenticate {
                withRole(ADMIN) {
                    get("/only-admins") {
                        call.respond(HttpStatusCode.OK)
                    }
                }
                withRole(USER) {
                    get("/only-users") {
                        call.respond(HttpStatusCode.OK)
                    }
                }
                withRole(ADMIN, USER) {
                    get("/admins-or-users") {
                        call.respond(HttpStatusCode.OK)
                    }
                }
                get("/all") {
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    )
}