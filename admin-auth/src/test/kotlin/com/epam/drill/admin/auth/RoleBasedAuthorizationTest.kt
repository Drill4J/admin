package com.epam.drill.admin.auth

import com.epam.drill.admin.auth.exception.NotAuthorizedException
import com.epam.drill.admin.auth.model.Role
import com.epam.drill.admin.auth.model.Role.ADMIN
import com.epam.drill.admin.auth.model.Role.USER
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
                call.respond(HttpStatusCode.Forbidden, "Forbidden")
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