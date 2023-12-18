package com.epam.drill.admin.auth.service

import com.epam.drill.admin.auth.model.ApiKeyCredentialsView
import com.epam.drill.admin.auth.model.ApiKeyView
import com.epam.drill.admin.auth.model.GenerateApiKeyPayload
import com.epam.drill.admin.auth.model.UserApiKeyView

interface ApiKeyService {
    suspend fun getAllApiKeys(): List<ApiKeyView>

    suspend fun getApiKeysByUser(userId: Int): List<UserApiKeyView>

    suspend fun getApiKeyById(id: Int): ApiKeyView

    suspend fun deleteApiKey(id: Int)

    suspend fun generateApiKey(userId: Int, payload: GenerateApiKeyPayload): ApiKeyCredentialsView
}