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
package com.epam.drill.admin.auth.config

import com.epam.drill.admin.auth.exception.NotAuthorizedException
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.principal.User
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*

class RoleBasedAuthConfiguration {
    var requiredRoles: Set<Role> = emptySet()
}

class AuthorizedRouteSelector(private val description: String) : RouteSelector() {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int) = RouteSelectorEvaluation.Constant

    override fun toString(): String = "(authorize ${description})"
}

class RoleBasedAuthPluginConfiguration {
    var throwErrorOnUnauthorizedResponse = false
}

private lateinit var pluginGlobalConfig: RoleBasedAuthPluginConfiguration
fun AuthenticationConfig.roleBasedAuthentication(config: RoleBasedAuthPluginConfiguration.() -> Unit = {}) {
    pluginGlobalConfig = RoleBasedAuthPluginConfiguration().apply(config)
}

private fun Route.buildAuthorizedRoute(
    requiredRoles: Set<Role>,
    build: Route.() -> Unit
): Route {
    val authorizedRoute = createChild(AuthorizedRouteSelector(requiredRoles.joinToString(",")))
    authorizedRoute.install(RoleBasedAuthPlugin) {
        this.requiredRoles = requiredRoles
    }
    authorizedRoute.build()
    return authorizedRoute
}

fun Route.withRole(vararg roles: Role, build: Route.() -> Unit) =
    buildAuthorizedRoute(requiredRoles = roles.toSet(), build = build)

val RoleBasedAuthPlugin =
    createRouteScopedPlugin(name = "RoleBasedAuthorization", createConfiguration = ::RoleBasedAuthConfiguration) {
        if (::pluginGlobalConfig.isInitialized.not()) {
            error("RoleBasedAuthPlugin not initialized. Setup plugin by calling AuthenticationConfig#roleBased in authenticate block")
        }
        with(pluginConfig) {
            on(AuthenticationChecked) { call ->
                val principal = call.principal<User>() ?: return@on
                if (!requiredRoles.contains(principal.role)) {
                    throw NotAuthorizedException()
                }
            }
        }
    }

class UnauthorizedAccessException(val denyReasons: MutableList<String>) : Exception()