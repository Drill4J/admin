package com.epam.drill.admin.auth.service

import com.epam.drill.admin.auth.model.*

interface ApiKeyService {
    suspend fun getAllApiKeys(): List<ApiKeyView>

    suspend fun getApiKeysByUser(userId: Int): List<UserApiKeyView>

    suspend fun deleteApiKey(id: Int)

    suspend fun generateApiKey(userId: Int, payload: GenerateApiKeyPayload): ApiKeyCredentialsView

    suspend fun signInThroughApiKey(apiKey: String): UserInfoView
}