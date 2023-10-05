package com.epam.drill.admin.users.route

import com.epam.drill.admin.users.service.UserAuthenticationService
import com.epam.drill.admin.users.view.ApiResponse
import com.epam.drill.admin.users.view.ChangePasswordForm
import com.epam.drill.admin.users.view.LoginForm
import com.epam.drill.admin.users.view.RegistrationForm
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance

class ProfileRoutes(override val di: DI) : DIAware {
    private val app by instance<Application>()
    private val service by instance<UserAuthenticationService>()

    init {
        app.routing {
            signInRoute()
            signUpRoute()
            updatePasswordRoute()
        }
    }

    fun Route.signInRoute() {
        post("sign-in") {
            val form = call.receive<LoginForm>()
            val token = service.signIn(form)
            call.response.header(HttpHeaders.Authorization, token.token)
            call.respond(HttpStatusCode.OK, token)
        }
    }

    fun Route.signUpRoute() {
        post("sign-up") {
            val form = call.receive<RegistrationForm>()
            service.signUp(form)
            call.respond(HttpStatusCode.OK, ApiResponse("User registered successfully. " +
                    "Please contact the administrator for system access."))
        }
    }

    fun Route.updatePasswordRoute() {
        post("update-password") {
            val form = call.receive<ChangePasswordForm>()

        }
    }
}

