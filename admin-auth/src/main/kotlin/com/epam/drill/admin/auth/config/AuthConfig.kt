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

import io.ktor.server.config.*

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
)