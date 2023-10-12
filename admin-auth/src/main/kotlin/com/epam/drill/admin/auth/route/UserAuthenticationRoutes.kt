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
package com.epam.drill.admin.auth.route

import com.epam.drill.admin.auth.exception.UserNotAuthenticatedException
import com.epam.drill.admin.auth.service.TokenService
import com.epam.drill.admin.auth.service.UserAuthenticationService
import com.epam.drill.admin.auth.view.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.serialization.Serializable
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance

class UserAuthenticationRoutes(override val di: DI) : DIAware {
    private val app by instance<Application>()
    private val authService by instance<UserAuthenticationService>()
    private val tokenService by instance<TokenService>()

    init {
        app.routing {
            loginRoute()
            signInRoute()
            signUpRoute()
            updatePasswordRoute()
        }
    }

    private fun Route.signInRoute() {
        post("sign-in") {
            val form = call.receive<LoginForm>()
            val view = authService.signIn(form)
            val token = tokenService.issueToken(view)
            call.response.header(HttpHeaders.Authorization, token)
            call.respond(HttpStatusCode.OK, TokenResponse(token))
        }
    }

    private fun Route.signUpRoute() {
        post("sign-up") {
            val form = call.receive<RegistrationForm>()
            authService.signUp(form)
            call.respond(HttpStatusCode.OK, ApiResponse("User registered successfully. " +
                    "Please contact the administrator for system access."))
        }
    }

    private fun Route.updatePasswordRoute() {
        authenticate("jwt") {
            post("update-password") {
                val form = call.receive<ChangePasswordForm>()
                val principal = call.principal<UserIdPrincipal>() ?: throw UserNotAuthenticatedException()
                authService.updatePassword(principal, form)
                call.respond(HttpStatusCode.OK, ApiResponse("Password changed successfully."))
            }
        }
    }

    private fun Route.loginRoute() {
        post("/api/login") {
            val form = call.receive<UserData>()
            val view = authService.signIn(LoginForm(username = form.name, password = form.password))
            val token = tokenService.issueToken(view)
            call.response.header(HttpHeaders.Authorization, token)
            call.respond(HttpStatusCode.OK, TokenResponse(token))
        }
    }

    @Serializable
    data class UserData(
        val name: String,
        val password: String,
    )
}

