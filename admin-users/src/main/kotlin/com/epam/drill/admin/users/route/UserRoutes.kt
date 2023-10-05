package com.epam.drill.admin.users.route

import com.epam.drill.admin.users.getRequiredParam
import com.epam.drill.admin.users.view.UserForm
import io.ktor.application.*
import io.ktor.request.*
import io.ktor.routing.*
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance

class UsersRoutes(override val di: DI) : DIAware {
    private val app by instance<Application>()

    init {
        app.routing {
            getUsersRoute()
            getUserRoute()
            editUserRoute()
            deleteUserRoute()
            blockUserRoute()
            unblockUserRoute()
            resetPasswordRoute()
        }
    }
}

fun Route.getUsersRoute() {
    get("/users") {

    }
}

fun Route.getUserRoute() {
    get("/users/{userId}") {
        val userId = call.getRequiredParam<Int>("userId")

    }
}

fun Route.editUserRoute() {
    put("/users/{userId}") {
        val userId = call.getRequiredParam<Int>("userId")
        val userForm = call.receive<UserForm>()

    }
}

fun Route.deleteUserRoute() {
    delete("/users/{userId}") {
        val userId = call.getRequiredParam<Int>("userId")

    }
}
fun Route.blockUserRoute() {
    patch("/users/{userId}/block") {
        val userId = call.getRequiredParam<Int>("userId")

    }
}

fun Route.unblockUserRoute() {
    patch("/users/{userId}/unblock") {
        val userId = call.getRequiredParam<Int>("userId")

    }
}

fun Route.resetPasswordRoute() {
    patch("/users/{userId}/reset-password") {
        val userId = call.getRequiredParam<Int>("userId")

    }
}