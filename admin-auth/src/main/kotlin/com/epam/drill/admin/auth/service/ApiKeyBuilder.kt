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
package com.epam.drill.admin.auth.service

/**
 * A builder for API keys.
 */
interface ApiKeyBuilder {
    /**
     * Converts the API key to string format.
     * @param apiKey the API key
     * @return the string representation
     */
    fun format(apiKey: ApiKey): String

    /**
     * Parses the API key from string format.
     * @param apiKey the string representation
     * @return the API key
     */
    fun parse(apiKey: String): ApiKey
}

/**
 * A model for API keys.
 */
data class ApiKey(val identifier: Int, val secret: String)