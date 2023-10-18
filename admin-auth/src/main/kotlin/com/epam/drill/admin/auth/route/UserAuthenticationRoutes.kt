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

import com.epam.drill.admin.auth.exception.*
import com.epam.drill.admin.auth.service.TokenService
import com.epam.drill.admin.auth.service.UserAuthenticationService
import com.epam.drill.admin.auth.view.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI as di

private val logger = KotlinLogging.logger {}

fun StatusPages.Configuration.authStatusPages() {
    exception<UserNotAuthenticatedException> { cause ->
        logger.trace(cause) { "401 User is not authenticated" }
        call.respond(HttpStatusCode.Unauthorized, "User is not authenticated")
    }
    exception<IncorrectCredentialsException> { cause ->
        logger.trace(cause) { "401 Username or password is incorrect" }
        call.respond(HttpStatusCode.Unauthorized, "Username or password is incorrect")
    }
    exception<UserValidationException> { cause ->
        logger.trace(cause) { "400 User data is invalid" }
        call.respond(HttpStatusCode.BadRequest, "User data is invalid")
    }
}

fun Routing.userAuthenticationRoutes() {
    loginRoute()
    signInRoute()
    signUpRoute()
    authenticate("jwt") {
        updatePasswordRoute()
    }
}

fun Route.signInRoute() {
    val authService by di().instance<UserAuthenticationService>()
    val tokenService by di().instance<TokenService>()

    post("sign-in") {
        val loginPayload = call.receive<LoginPayload>()
        val userView = authService.signIn(loginPayload)
        val token = tokenService.issueToken(userView)
        call.response.header(HttpHeaders.Authorization, token)
        call.respond(HttpStatusCode.OK, TokenView(token))
    }
}

fun Route.signUpRoute() {
    val authService by di().instance<UserAuthenticationService>()

    post("sign-up") {
        val form = call.receive<RegistrationPayload>()
        authService.signUp(form)
        call.respond(
            HttpStatusCode.OK, MessageView(
                "User registered successfully. " +
                        "Please contact the administrator for system access."
            )
        )
    }
}

fun Route.updatePasswordRoute() {
    val authService by di().instance<UserAuthenticationService>()

    post("update-password") {
        val changePasswordPayload = call.receive<ChangePasswordPayload>()
        val principal = call.principal<UserIdPrincipal>() ?: throw UserNotAuthenticatedException()
        authService.updatePassword(principal, changePasswordPayload)
        call.respond(HttpStatusCode.OK, MessageView("Password changed successfully."))
    }
}

@Deprecated("The /api/login route is outdated, please use /sign-in")
fun Route.loginRoute() {
    val authService by di().instance<UserAuthenticationService>()
    val tokenService by di().instance<TokenService>()

    post("/api/login") {
        val loginPayload = call.receive<UserData>()
        val userView = authService.signIn(LoginPayload(username = loginPayload.name, password = loginPayload.password))
        val token = tokenService.issueToken(userView)
        call.response.header(HttpHeaders.Authorization, token)
        call.respond(HttpStatusCode.OK, TokenView(token))
    }
}

@Serializable
data class UserData(
    val name: String,
    val password: String,
)