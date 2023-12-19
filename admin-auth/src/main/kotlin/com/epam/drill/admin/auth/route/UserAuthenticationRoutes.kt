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

import com.epam.drill.admin.auth.config.SimpleAuthConfig
import com.epam.drill.admin.auth.exception.*
import com.epam.drill.admin.auth.service.TokenService
import com.epam.drill.admin.auth.service.UserAuthenticationService
import com.epam.drill.admin.auth.model.*
import com.epam.drill.admin.auth.principal.User
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.locations.post
import io.ktor.request.*
import io.ktor.response.header
import io.ktor.routing.*
import mu.KotlinLogging
import org.kodein.di.instance
import org.kodein.di.instanceOrNull
import org.kodein.di.ktor.closestDI as di

private val logger = KotlinLogging.logger {}

@Location("/sign-in")
object SignIn

@Location("/sign-up")
object SignUp

@Location("/user-info")
object UserInfo

@Location("/update-password")
object UpdatePassword

@Deprecated("Use /sign-in")
@Location("/api/login")
object Login

/**
 * The Ktor StatusPages plugin configuration for simple authentication status pages.
 */
fun StatusPages.Configuration.authStatusPages() {
    exception<NotAuthenticatedException> { cause ->
        logger.trace(cause) { "401 User is not authenticated" }
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
    exception<ForbiddenOperationException> { cause ->
        logger.trace(cause) { "422 Cannot modify own profile" }
        call.unprocessableEntity(cause)
    }
    exception<UserNotFoundException> { cause ->
        logger.trace(cause) { "404 User not found" }
        call.notFound(cause)
    }
    exception<ApiKeyNotFoundException> { cause ->
        logger.trace(cause) { "404 Api key not found" }
        call.notFound(cause)
    }
}

/**
 * A user authentication and registration routes configuration.
 */
fun Route.userAuthenticationRoutes() {
    signInRoute()
    signUpRoute()
}

/**
 * A user profile routes configuration.
 */
fun Route.userProfileRoutes() {
    userInfoRoute()
    updatePasswordRoute()
}

/**
 * A user authentication route configuration.
 */
fun Route.signInRoute() {
    val authService by di().instance<UserAuthenticationService>()
    val tokenService by di().instance<TokenService>()

    post<SignIn> {
        val loginPayload = call.receive<LoginPayload>()
        val userView = authService.signIn(loginPayload)
        val token = tokenService.issueToken(userView)
        call.response.header(HttpHeaders.Authorization, token)
        call.ok(TokenView(token), "User successfully authenticated.")
    }
}

/**
 * A user registration route configuration.
 */
fun Route.signUpRoute() {
    val authService by di().instance<UserAuthenticationService>()
    val simpleAuthConfig by di().instanceOrNull<SimpleAuthConfig>()

    if (simpleAuthConfig?.signUpEnabled != false) {
        post<SignUp> {
            val payload = call.receive<RegistrationPayload>()
            authService.signUp(payload)
            call.ok(
                "User registration request accepted. " +
                        "Please contact the administrator to confirm the registration."
            )
        }
    }
}

/**
 * A user profile route configuration.
 */
fun Route.userInfoRoute() {
    val authService by di().instance<UserAuthenticationService>()

    get<UserInfo> {
        val principal = call.principal<User>() ?: throw NotAuthenticatedException()
        val userInfoView = authService.getUserInfo(principal)
        call.ok(userInfoView)
    }
}

/**
 * An update password route configuration.
 */
fun Route.updatePasswordRoute() {
    val authService by di().instance<UserAuthenticationService>()

    post<UpdatePassword> {
        val changePasswordPayload = call.receive<ChangePasswordPayload>()
        val principal = call.principal<User>() ?: throw NotAuthenticatedException()
        authService.updatePassword(principal, changePasswordPayload)
        call.ok("Password successfully changed.")
    }
}