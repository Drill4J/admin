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

import com.epam.drill.admin.auth.principal.Role
import io.ktor.config.*

class OAuthConfig(private val config: ApplicationConfig) {

    private val drill: ApplicationConfig
        get() = config.config("drill")

    private val ui: ApplicationConfig
        get() = drill.config("ui")

    private val oauth2: ApplicationConfig
        get() = drill
            .config("auth")
            .config("oauth2")

    val authorizeUrl: String
        get() = oauth2.property("authorizeUrl").getString()

    val accessTokenUrl: String
        get() = oauth2.property("accessTokenUrl").getString()

    val userInfoUrl: String?
        get() = oauth2.propertyOrNull("userInfoUrl")?.getString()

    val clientId: String
        get() = oauth2.property("clientId").getString()

    val clientSecret: String
        get() = oauth2.property("clientSecret").getString()

    val scopes: List<String>
        get() = oauth2.propertyOrNull("scopes")?.getString()
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: listOf()

    val uiRootUrl: String
        get() =  ui.property("rootUrl").getString()

    val uiRootPath: String
        get() = ui.propertyOrNull("rootPath")?.getString() ?: "/"

    val tokenMapping: UserMapping
        get() = oauth2.config("tokenMapping").run {
            UserMapping(
                username = propertyOrNull("username")?.getString() ?: "sub", //see https://openid.net/specs/openid-connect-core-1_0.html#StandardClaims
                roles = propertyOrNull("roles")?.getString()
            )
        }

    val userInfoMapping: UserMapping
        get() = oauth2.config("userInfoMapping").run {
            UserMapping(
                username = propertyOrNull("username")?.getString() ?: "username",
                roles = propertyOrNull("roles")?.getString()
            )
        }

    val roleMapping: RoleMapping
        get() = oauth2.config("roleMapping").run {
            RoleMapping(
                user = propertyOrNull("user")?.getString() ?: Role.USER.name,
                admin = propertyOrNull("admin")?.getString() ?: Role.ADMIN.name
            )
        }
}

data class UserMapping(val username: String, val roles: String?)
data class RoleMapping(val user: String, val admin: String)
