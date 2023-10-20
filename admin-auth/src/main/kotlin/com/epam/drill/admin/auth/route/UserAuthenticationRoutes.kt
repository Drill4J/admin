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
import com.epam.drill.admin.auth.model.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.Route
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI as di

private val logger = KotlinLogging.logger {}

@Location("/sign-in")
object SignIn

@Location("/sign-up")
object SignUp

@Location("/update-password")
object UpdatePassword

@Deprecated("Use /sign-in")
@Location("/api/login")
object Login

fun StatusPages.Configuration.authStatusPages() {
    exception<NotAuthenticatedException> { cause ->
        logger.trace(cause) { "401 User is not authenticated" }
        call.unauthorizedError(cause)
    }
    exception<IncorrectCredentialsException> { cause ->
        logger.trace(cause) { "401 Username or password is incorrect" }
        call.unauthorizedError(cause)
    }
    exception<UserValidationException> { cause ->
        logger.trace(cause) { "400 User data is invalid" }
        call.validationError(cause)
    }
    exception<NotAuthorizedException> { cause ->
        logger.trace(cause) { "403 Access denied" }
        call.accessDeniedError(cause)
    }
}

fun Route.userAuthenticationRoutes() {
    loginRoute()
    signInRoute()
    signUpRoute()
}

fun Route.signInRoute() {
    val authService by di().instance<UserAuthenticationService>()
    val tokenService by di().instance<TokenService>()

    post<SignIn> {
        val loginPayload = call.receive<LoginPayload>()
        val userView = authService.signIn(loginPayload)
        val token = tokenService.issueToken(userView)
        call.response.header(HttpHeaders.Authorization, token)
        call.ok(TokenView(token), "User was successfully authenticated.")
    }
}

fun Route.signUpRoute() {
    val authService by di().instance<UserAuthenticationService>()

    post<SignUp> {
        val payload = call.receive<RegistrationPayload>()
        authService.signUp(payload)
        call.ok(
            "User registration request accepted. " +
                    "Please contact the administrator to confirm the registration."
        )
    }
}

fun Route.updatePasswordRoute() {
    val authService by di().instance<UserAuthenticationService>()

    post<UpdatePassword> {
        val changePasswordPayload = call.receive<ChangePasswordPayload>()
        val principal = call.principal<UserIdPrincipal>() ?: throw NotAuthenticatedException()
        authService.updatePassword(principal, changePasswordPayload)
        call.ok("Password was successfully changed.")
    }
}

@Deprecated("The /api/login route is outdated, please use /sign-in")
fun Route.loginRoute() {
    val authService by di().instance<UserAuthenticationService>()
    val tokenService by di().instance<TokenService>()

    post<Login> {
        val loginPayload = call.receive<UserData>()
        val userView = authService.signIn(LoginPayload(username = loginPayload.name, password = loginPayload.password))
        val token = tokenService.issueToken(userView)
        call.response.header(HttpHeaders.Authorization, token)
        call.respond(HttpStatusCode.OK, TokenView(token))
    }
}

@Serializable
@Deprecated("use LoginPayload")
data class UserData(
    val name: String,
    val password: String,
)