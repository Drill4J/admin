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

import io.ktor.config.*

enum class AuthType {
    SIMPLE,
    OAUTH2,
    SIMPLE_WITH_OAUTH2
}

/**
 * A configuration for authentication.
 * @param config the Ktor configuration
 * @param simpleAuth the simple authentication configuration
 * @param oauth2 the OAuth2 configuration
 * @param jwt the JWT configuration
 */
class AuthConfig(
    private val config: ApplicationConfig,
    val simpleAuth: SimpleAuthConfig? = null,
    val oauth2: OAuth2Config? = null,
    val jwt: JwtConfig
) {
    val type: AuthType
        get() = config.getAuthType()
}

/**
 * Gets an authentication type of the Drill4J Admin.
 *
 * @receiver the configuration in which Drill4J authentication is set
 * @return the authentication type
 * @throws IllegalArgumentException if the authentication type is unknown
 */
fun ApplicationConfig.getAuthType() = propertyOrNull("type")
    ?.getString()
    ?.let { authType ->
        when (authType.uppercase()) {
            AuthType.SIMPLE.name -> AuthType.SIMPLE
            AuthType.OAUTH2.name -> AuthType.OAUTH2
            AuthType.SIMPLE_WITH_OAUTH2.name -> AuthType.SIMPLE_WITH_OAUTH2
            else -> throw IllegalArgumentException("Unknown auth type \"$authType\". " +
                    "Please set the env variable DRILL_AUTH_TYPE to either " +
                    "${
                        AuthType.values().joinToString(separator = "\", \"", prefix = "\"", postfix = "\"") { it.name }
                    }."
            )
        }
    } ?: AuthType.SIMPLE