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
 * A configuration for simple authentication.
 * @param config the Ktor configuration
 */
class SimpleAuthConfig(
    private val config: ApplicationConfig
) {
    /**
     * A flag indicating whether the simple form-based authentication is enabled. Optional, true by default.
     */
    val enabled: Boolean
        get() = config.property("enabled").getString().toBoolean()

    /**
     * A flag indicating whether the sign-up is enabled. Optional, true by default.
     */
    val signUpEnabled: Boolean
        get() = config.propertyOrNull("signUpEnabled")?.getString()?.toBoolean() ?: true
}