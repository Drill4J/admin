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

import com.epam.drill.admin.auth.service.UserManagementService
import com.epam.drill.admin.auth.view.MessageView
import com.epam.drill.admin.auth.view.UserPayload
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI as di

fun Routing.userManagementRoutes() {
    route("/api") {
        getUsersRoute()
        getUserRoute()
        editUserRoute()
        deleteUserRoute()
        blockUserRoute()
        unblockUserRoute()
        resetPasswordRoute()
    }
}

fun Route.getUsersRoute() {
    val service by di().instance<UserManagementService>()

    get("/users") {
        val users = service.getUsers()
        call.respond(HttpStatusCode.OK, users)
    }
}

fun Route.getUserRoute() {
    val service by di().instance<UserManagementService>()

    get("/users/{userId}") {
        val userId = call.getRequiredParam<Int>("userId")
        val userView = service.getUser(userId)
        call.respond(HttpStatusCode.OK, userView)
    }
}

fun Route.editUserRoute() {
    val service by di().instance<UserManagementService>()

    put("/users/{userId}") {
        val userId = call.getRequiredParam<Int>("userId")
        val userPayload = call.receive<UserPayload>()
        val userView = service.updateUser(userId, userPayload)
        call.respond(HttpStatusCode.OK, userView)
    }
}

fun Route.deleteUserRoute() {
    val service by di().instance<UserManagementService>()

    delete("/users/{userId}") {
        val userId = call.getRequiredParam<Int>("userId")
        service.deleteUser(userId)
        call.respond(HttpStatusCode.OK, MessageView("User deleted successfully"))
    }
}

fun Route.blockUserRoute() {
    val service by di().instance<UserManagementService>()

    patch("/users/{userId}/block") {
        val userId = call.getRequiredParam<Int>("userId")
        service.blockUser(userId)
        call.respond(HttpStatusCode.OK, MessageView("User blocked successfully"))
    }
}

fun Route.unblockUserRoute() {
    val service by di().instance<UserManagementService>()

    patch("/users/{userId}/unblock") {
        val userId = call.getRequiredParam<Int>("userId")
        service.unblockUser(userId)
        call.respond(HttpStatusCode.OK, MessageView("User unblocked successfully"))
    }
}

fun Route.resetPasswordRoute() {
    val service by di().instance<UserManagementService>()

    patch("/users/{userId}/reset-password") {
        val userId = call.getRequiredParam<Int>("userId")
        val credentialsView = service.resetPassword(userId)
        call.respond(HttpStatusCode.OK, credentialsView)
    }
}

