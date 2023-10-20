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
import com.epam.drill.admin.auth.view.EditUserPayload
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.Route
import io.ktor.routing.Routing
import io.ktor.routing.route
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI as di

@Location("/users")
object Users {
    @Location("/{userId}")
    data class Id(val userId: Int)

    @Location("/{userId}/block")
    data class Block(val userId: Int)

    @Location("/{userId}/unblock")
    data class Unblock(val userId: Int)

    @Location("/{userId}/reset-password")
    data class ResetPassword(val userId: Int)
}

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

    get<Users> {
        val users = service.getUsers()
        call.respond(HttpStatusCode.OK, users)
    }
}

fun Route.getUserRoute() {
    val service by di().instance<UserManagementService>()

    get<Users.Id> { params ->
        val userView = service.getUser(params.userId)
        call.respond(HttpStatusCode.OK, userView)
    }
}

fun Route.editUserRoute() {
    val service by di().instance<UserManagementService>()

    put<Users.Id> { (userId) ->
        val editUserPayload = call.receive<EditUserPayload>()
        val userView = service.updateUser(userId, editUserPayload)
        call.respond(HttpStatusCode.OK, userView)
    }
}

fun Route.deleteUserRoute() {
    val service by di().instance<UserManagementService>()

    delete<Users.Id> { (userId) ->
        service.deleteUser(userId)
        call.respond(HttpStatusCode.OK, MessageView("User deleted successfully"))
    }
}

fun Route.blockUserRoute() {
    val service by di().instance<UserManagementService>()

    patch<Users.Block> { (userId) ->
        service.blockUser(userId)
        call.respond(HttpStatusCode.OK, MessageView("User blocked successfully"))
    }
}

fun Route.unblockUserRoute() {
    val service by di().instance<UserManagementService>()

    patch<Users.Unblock> { (userId) ->
        service.unblockUser(userId)
        call.respond(HttpStatusCode.OK, MessageView("User unblocked successfully"))
    }
}

fun Route.resetPasswordRoute() {
    val service by di().instance<UserManagementService>()

    patch<Users.ResetPassword> { (userId) ->
        val credentialsView = service.resetPassword(userId)
        call.respond(HttpStatusCode.OK, credentialsView)
    }
}

