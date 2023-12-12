package com.epam.drill.admin.auth.repository

import com.epam.drill.admin.auth.entity.ApiKeyEntity

interface ApiKeyRepository {
    suspend fun getAllApiKeys(): List<ApiKeyEntity>
    suspend fun getApiKeysByUserId(userId: Int): List<ApiKeyEntity>
    suspend fun deleteApiKey(id: Int)
    suspend fun createApiKey(entity: ApiKeyEntity): ApiKeyEntity
}