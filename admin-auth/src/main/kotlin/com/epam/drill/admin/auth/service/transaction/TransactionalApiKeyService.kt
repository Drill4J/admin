package com.epam.drill.admin.auth.service.transaction

import com.epam.drill.admin.auth.model.GenerateApiKeyPayload
import com.epam.drill.admin.auth.service.ApiKeyService
import com.epam.drill.admin.auth.config.DatabaseConfig.transaction

class TransactionalApiKeyService(private val delegate: ApiKeyService
) : ApiKeyService by delegate {
    override suspend fun getAllApiKeys() = transaction {
        delegate.getAllApiKeys()
    }

    override suspend fun getApiKeysByUser(userId: Int) = transaction {
        delegate.getApiKeysByUser(userId)
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