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
package com.epam.drill.admin.users.route

import com.epam.drill.admin.users.service.UserManagementService
import com.epam.drill.admin.users.view.ApiResponse
import com.epam.drill.admin.users.view.UserForm
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance

class UsersRoutes(override val di: DI) : DIAware {
    private val app by instance<Application>()
    private val service by instance<UserManagementService>()

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

    fun Route.getUsersRoute() {
        get("/users") {
            call.respond(HttpStatusCode.OK, service.getUsers())
        }
    }

    fun Route.getUserRoute() {
        get("/users/{userId}") {
            val userId = call.getRequiredParam<Int>("userId")
            call.respond(HttpStatusCode.OK, service.getUser(userId))
        }
    }

    fun Route.editUserRoute() {
        put("/users/{userId}") {
            val userId = call.getRequiredParam<Int>("userId")
            val userForm = call.receive<UserForm>()
            service.updateUser(userId, userForm)
            call.respond(HttpStatusCode.OK, ApiResponse("User updated successfully"))
        }
    }

    fun Route.deleteUserRoute() {
        delete("/users/{userId}") {
            val userId = call.getRequiredParam<Int>("userId")
            service.deleteUser(userId)
            call.respond(HttpStatusCode.OK, ApiResponse("User deleted successfully"))
        }
    }
    fun Route.blockUserRoute() {
        patch("/users/{userId}/block") {
            val userId = call.getRequiredParam<Int>("userId")
            service.blockUser(userId)
            call.respond(HttpStatusCode.OK, ApiResponse("User blocked successfully"))
        }
    }

    fun Route.unblockUserRoute() {
        patch("/users/{userId}/unblock") {
            val userId = call.getRequiredParam<Int>("userId")
            service.unblockUser(userId)
            call.respond(HttpStatusCode.OK, ApiResponse("User unblocked successfully"))
        }
    }

    fun Route.resetPasswordRoute() {
        patch("/users/{userId}/reset-password") {
            val userId = call.getRequiredParam<Int>("userId")
            call.respond(HttpStatusCode.OK, service.resetPassword(userId))
        }
    }
}

