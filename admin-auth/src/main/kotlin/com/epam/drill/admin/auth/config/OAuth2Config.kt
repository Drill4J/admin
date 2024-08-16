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
import io.ktor.server.config.*
import java.lang.Boolean.parseBoolean

/**
 * An OAuth2 configuration.
 * @param config the Ktor configuration
 */
class OAuth2Config(private val config: ApplicationConfig) {

    /**
     * A flag indicating whether the OAuth2 authentication is enabled. Optional, false by default.
     */
    val enabled: Boolean
        get() = config.property("enabled").getString().toBoolean()

    /**
     * An OAuth2 authorize URL.
     */
    val authorizeUrl: String
        get() = config.property("authorizeUrl").getString()

    /**
     * An OAuth2 access token URL.
     */
    val accessTokenUrl: String
        get() = config.property("accessTokenUrl").getString()

    /**
     * An OAuth2 user info URL. Optional, if not set, the request for user information will not be used.
     */
    val userInfoUrl: String?
        get() = config.propertyOrNull("userInfoUrl")?.getString()

    /**
     * A OAuth2 client ID.
     */
    val clientId: String
        get() = config.property("clientId").getString()

    /**
     * A OAuth2 client secret.
     */
    val clientSecret: String
        get() = config.property("clientSecret").getString()

    /**
     * Scopes to OAuth2 request. Optional, empty list by default.
     */
    val scopes: List<String>
        get() = config.propertyOrNull("scopes")?.getString()
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: listOf()

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
        get() = config.config("tokenMapping").run {
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
        get() = config.config("userInfoMapping").run {
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
        get() = config.config("roleMapping").run {
            RoleMapping(
                user = propertyOrNull("user")?.getString() ?: Role.USER.name,
                admin = propertyOrNull("admin")?.getString() ?: Role.ADMIN.name
            )
        }

    /**
     * Application root URL to redirect after successfully authentication
     */
    val redirectUrl: String
        get() = config.property("redirectUrl").getString()

    /**
     * A title of the OAuth2 sign-in button. Optional, "Sign in with SSO" by default.
     */
    val signInButtonTitle: String
        get() = config.propertyOrNull("buttonTitle")?.getString() ?: "Sign in with Auth Provider"

    /**
     * A flag that indicates whether the automatic sign-in is enabled. Optional, true by default.
     */
    val automaticSignIn: Boolean
        get() = config.propertyOrNull("automaticSignIn")?.getString()?.let { parseBoolean(it) } ?: false
}

data class UserMapping(val username: String, val roles: String?)
data class RoleMapping(val user: String, val admin: String)
