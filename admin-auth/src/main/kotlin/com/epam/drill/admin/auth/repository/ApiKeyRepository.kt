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
package com.epam.drill.admin.auth.repository

import com.epam.drill.admin.auth.entity.ApiKeyEntity

/**
 * A repository for storing API keys.
 */
interface ApiKeyRepository {
    /**
     * Finds all API keys.
     * @return the list of API keys
     */
    suspend fun findAll(): List<ApiKeyEntity>

    /**
     * Finds all API keys by user id.
     * @param userId the id of the user
     * @return the list of API keys
     */
    suspend fun findAllByUserId(userId: Int): List<ApiKeyEntity>

    /**
     * Finds an API key by id.
     * @param id the id of the API key
     * @return the API key
     */
    suspend fun findById(id: Int): ApiKeyEntity?

    /**
     * Deletes an API key.
     * @param id the id of the API key
     */
    suspend fun deleteById(id: Int)

    /**
     * Creates a new API key.
     * @param entity the API key to be created
     * @return the created API key
     */
    suspend fun create(entity: ApiKeyEntity): ApiKeyEntity
}