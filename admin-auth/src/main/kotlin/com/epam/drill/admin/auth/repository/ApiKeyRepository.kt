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

interface ApiKeyRepository {
    suspend fun findAll(): List<ApiKeyEntity>
    suspend fun findAllByUserId(userId: Int): List<ApiKeyEntity>
    suspend fun findById(id: Int): ApiKeyEntity?
    suspend fun deleteById(id: Int)
    suspend fun create(entity: ApiKeyEntity): ApiKeyEntity
}