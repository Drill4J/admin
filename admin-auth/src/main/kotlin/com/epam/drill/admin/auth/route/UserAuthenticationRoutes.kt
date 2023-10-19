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
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.Routing
import io.ktor.routing.Route
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI as di

@Location("/sign-in")
object SignIn
@Location("/sign-up")
object SignUp
@Location("/update-password")
object UpdatePassword
@Deprecated("Use /sign-in")
@Location("/api/login")
object Login


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

    post<SignIn> {
        val loginPayload = call.receive<LoginPayload>()
        val userView = authService.signIn(loginPayload)
        val token = tokenService.issueToken(userView)
        call.response.header(HttpHeaders.Authorization, token)
        call.respond(HttpStatusCode.OK, TokenView(token))
    }
}

fun Route.signUpRoute() {
    val authService by di().instance<UserAuthenticationService>()

    post<SignUp> {
        val payload = call.receive<RegistrationPayload>()
        authService.signUp(payload)
        call.respond(
            HttpStatusCode.OK, MessageView(
                "User registration request accepted. " +
                        "Please contact the administrator to confirm the registration."
            )
        )
    }
}

fun Route.updatePasswordRoute() {
    val authService by di().instance<UserAuthenticationService>()

    post<UpdatePassword> {
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