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

import com.epam.drill.admin.auth.exception.ForbiddenOperationException
import com.epam.drill.admin.auth.service.UserManagementService
import com.epam.drill.admin.auth.model.EditUserPayload
import com.epam.drill.admin.common.principal.User
import io.ktor.resources.*
import io.ktor.server.resources.get
import io.ktor.server.resources.put
import io.ktor.server.resources.delete
import io.ktor.server.resources.patch
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI as di
import com.epam.drill.admin.common.route.*

@Resource("/users")
class Users {
    @Resource("/{userId}")
    class Id(val parent: Users, val userId: Int)

    @Resource("/{userId}/block")
    class Block(val parent: Users, val userId: Int)

    @Resource("/{userId}/unblock")
    class Unblock(val parent: Users, val userId: Int)

    @Resource("/{userId}/reset-password")
    class ResetPassword(val parent: Users, val userId: Int)
}

/**
 * A set of routes for user management
 */
fun Route.userManagementRoutes() {
    getUsersRoute()
    getUserRoute()
    editUserRoute()
    deleteUserRoute()
    blockUserRoute()
    unblockUserRoute()
    resetPasswordRoute()
}

/**
 * A route for getting users
 */
fun Route.getUsersRoute() {
    val service by di().instance<UserManagementService>()

    get<Users> {
        val users = service.getUsers()
        call.ok(users)
    }
}

/**
 * A route for getting user by id
 */
fun Route.getUserRoute() {
    val service by di().instance<UserManagementService>()

    get<Users.Id> { params ->
        val userView = service.getUser(params.userId)
        call.ok(userView)
    }
}

/**
 * A route for editing user
 */
fun Route.editUserRoute() {
    val service by di().instance<UserManagementService>()

    put<Users.Id> {params ->
        throwExceptionIfCurrentUserIs(params.userId, "You cannot change your own role")
        val editUserPayload = call.receive<EditUserPayload>()
        val userView = service.updateUser(params.userId, editUserPayload)
        call.ok(userView, "User successfully edited.")
    }
}

/**
 * A route for deleting user by id
 */
fun Route.deleteUserRoute() {
    val service by di().instance<UserManagementService>()

    delete<Users.Id> { params ->
        throwExceptionIfCurrentUserIs(params.userId, "You cannot delete your own user")
        service.deleteUser(params.userId)
        call.ok("User successfully deleted.")
    }
}

/**
 * A route for blocking user by id
 */
fun Route.blockUserRoute() {
    val service by di().instance<UserManagementService>()

    patch<Users.Block> { params ->
        throwExceptionIfCurrentUserIs(params.userId, "You cannot block your own user")
        service.blockUser(params.userId)
        call.ok("User successfully blocked.")
    }
}

/**
 * A route for unblocking user by id
 */
fun Route.unblockUserRoute() {
    val service by di().instance<UserManagementService>()

    patch<Users.Unblock> { params ->
        service.unblockUser(params.userId)
        call.ok("User successfully unblocked.")
    }
}

/**
 * A reset user password route
 */
fun Route.resetPasswordRoute() {
    val service by di().instance<UserManagementService>()

    patch<Users.ResetPassword> { params ->
        val credentialsView = service.resetPassword(params.userId)
        call.ok(credentialsView, "Password reset successfully.")
    }
}

private fun PipelineContext<Unit, ApplicationCall>.throwExceptionIfCurrentUserIs(userId: Int, message: String) {
    if (call.principal<User>()?.id == userId)
        throw ForbiddenOperationException(message)
}