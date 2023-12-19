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

import com.epam.drill.admin.auth.model.*

/**
 * A service for managing API keys.
 */
interface ApiKeyService {
    /**
     * Gets all API keys.
     * @return the list of all API keys
     */
    suspend fun getAllApiKeys(): List<ApiKeyView>

    /**
     * Gets API keys by user.
     * @param userId the ID of the user
     * @return the list of API keys
     */
    suspend fun getApiKeysByUser(userId: Int): List<UserApiKeyView>

    /**
     * Gets an API key by ID.
     * @param id the ID of the API key
     * @return the API key
     */
    suspend fun getApiKeyById(id: Int): ApiKeyView

    /**
     * Deletes an API key.
     * @param id the ID of the API key
     */
    suspend fun deleteApiKey(id: Int)

    /**
     * Generates a new API key.
     * @param userId the ID of the user to whom the API key belongs
     * @param payload the payload of the request
     * @return the generated API key
     */
    suspend fun generateApiKey(userId: Int, payload: GenerateApiKeyPayload): ApiKeyCredentialsView

    /**
     * Signs in through an API key.
     * @param apiKey the API key to sign in with
     * @return the user info
     */
    suspend fun signInThroughApiKey(apiKey: String): UserInfoView
}