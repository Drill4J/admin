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
package com.epam.drill.admin.auth.service.transaction

import com.epam.drill.admin.auth.model.GenerateApiKeyPayload
import com.epam.drill.admin.auth.service.ApiKeyService
import com.epam.drill.admin.auth.config.AuthDatabaseConfig.transaction

class TransactionalApiKeyService(private val delegate: ApiKeyService
) : ApiKeyService by delegate {
    override suspend fun getAllApiKeys() = transaction {
        delegate.getAllApiKeys()
    }

    override suspend fun getApiKeysByUser(userId: Int) = transaction {
        delegate.getApiKeysByUser(userId)
    }

    override suspend fun getApiKeyById(id: Int) = transaction {
        delegate.getApiKeyById(id)
    }

    override suspend fun deleteApiKey(id: Int) = transaction {
        delegate.deleteApiKey(id)
    }

    override suspend fun generateApiKey(userId: Int, payload: GenerateApiKeyPayload) = transaction {
        delegate.generateApiKey(userId, payload)
    }

    override suspend fun signInThroughApiKey(apiKey: String) = transaction {
        delegate.signInThroughApiKey(apiKey)
    }
}