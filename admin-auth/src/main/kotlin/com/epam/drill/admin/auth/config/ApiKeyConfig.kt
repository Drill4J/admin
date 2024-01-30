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

/**
 * The API Key configuration.
 * @param config the Ktor configuration
 */
class ApiKeyConfig(private val config: ApplicationConfig) {
    /**
     * A length in bytes of the secret part of the API key. Optional, 32 by default.
     */
    val secretLength: Int
        get() = config.propertyOrNull("secretLength")?.getString()?.toInt() ?: 32

    /**
     * The maximum number of items or data size that the cache can hold before it starts replacing or removing existing entries.
     * By default, 1000.
     */
    val maximumCacheSize: Long
        get() = config.propertyOrNull("maximumCacheSize")?.getString()?.toLong() ?: 1000

    /**
     * Duration in minutes after which the stored data in the cache becomes invalid or expired.
     * By default, 60 minutes.
     */
    val ttlCacheInMinutes: Long
        get() = config.propertyOrNull("ttlCacheInMinutes")?.getString()?.toLong() ?: 60

}
