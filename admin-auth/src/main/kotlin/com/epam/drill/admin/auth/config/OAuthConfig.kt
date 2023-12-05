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

/**
 * A Ktor configuration for OAuth2 based on the key "drill.auth.oauth2".
 */
class OAuthConfig(private val config: ApplicationConfig) {

    private val drill: ApplicationConfig
        get() = config.config("drill")

    private val ui: ApplicationConfig
        get() = drill.config("ui")

    private val oauth2: ApplicationConfig
        get() = drill
            .config("auth")
            .config("oauth2")

    /**
     * An OAuth2 authorize URL.
     */
    val authorizeUrl: String
        get() = oauth2.property("authorizeUrl").getString()

    /**
     * An OAuth2 access token URL.
     */
    val accessTokenUrl: String
        get() = oauth2.property("accessTokenUrl").getString()

    /**
     * An OAuth2 user info URL. Optional, if not set, the request for user information will not be used.
     */
    val userInfoUrl: String?
        get() = oauth2.propertyOrNull("userInfoUrl")?.getString()

    /**
     * A OAuth2 client ID.
      */
    val clientId: String
        get() = oauth2.property("clientId").getString()

    /**
     * A OAuth2 client secret.
     */
    val clientSecret: String
        get() = oauth2.property("clientSecret").getString()

    /**
     * Scopes to OAuth2 request. Optional, empty list by default.
     */
    val scopes: List<String>
        get() = oauth2.propertyOrNull("scopes")?.getString()
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: listOf()

    /**
     * UI application root URL. The key is "drill.ui.rootUrl".
     */
    val uiRootUrl: String
        get() =  ui.property("rootUrl").getString()

    /**
     * UI application root path. The key is "drill.ui.rootPath". Optional, "/" by default.
     */
    val uiRootPath: String
        get() = ui.propertyOrNull("rootPath")?.getString() ?: "/"

    /**
     * OAuth2 access token payload mapping.
     *
     * Used for mapping access token claims to [com.epam.drill.admin.auth.model.UserInfoView].
     * Includes username and roles mapping:
     *   "username" must contain name of a claim. Value of that claim is going to be treated as username.
     *   "roles" must contain name of a claim which value contains list of roles. Leave empty for no mapping.
     * NOTE: This is only the _name_ of the claim containing roles - actual mapping is specified in [roleMapping] section
     *
     * @see [Standard claims](https://openid.net/specs/openid-connect-core-1_0.html#StandardClaims)
     */
    val tokenMapping: UserMapping
        get() = oauth2.config("tokenMapping").run {
            UserMapping(
                username = propertyOrNull("username")?.getString() ?: "sub",
                roles = propertyOrNull("roles")?.getString()
            )
        }

    /**
     * OAuth2 user info mapping.
     *
     *
     * Used for map response to [com.epam.drill.admin.auth.model.UserInfoView].
     * Includes username and roles mapping:
     *   "username" must contain name of a field in /user-info response body. Value of that field is going to be treated as username.
     *   "roles" must contain name of a field in /user-info response body response which value contains list of roles. Leave empty for no mapping.
     * NOTE: This is only the _name_ of the field containing roles - actual mapping is specified in [roleMapping] section
     * NOTE: If [userInfoUrl] is specified values extracted from token [tokenMapping] are ignored (values mapped from /user-info take precedence)
     */
    val userInfoMapping: UserMapping
        get() = oauth2.config("userInfoMapping").run {
            UserMapping(
                username = propertyOrNull("username")?.getString() ?: "username",
                roles = propertyOrNull("roles")?.getString()
            )
        }

    /**
     * Mapping configuration from OAuth2 roles to Drill4J roles.
     *
     * Used for map a list of OAuth2 roles to [com.epam.drill.admin.auth.principal.Role].
     * Includes user and admin roles mapping:
     * A user role mapping is a name of OAuth2 role matching the Drill4J user role. Optional, "user" by default.
     * An admin role mapping is a name of OAuth2 role matching the Drill4J admin role. Optional, "admin" by default.
     */
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
