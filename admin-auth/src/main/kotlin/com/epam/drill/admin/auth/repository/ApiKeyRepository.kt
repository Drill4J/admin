package com.epam.drill.admin.auth.repository

import com.epam.drill.admin.auth.entity.ApiKeyEntity

interface ApiKeyRepository {
    suspend fun getAll(): List<ApiKeyEntity>
    suspend fun getAllByUserId(userId: Int): List<ApiKeyEntity>
    suspend fun delete(id: Int)
    suspend fun create(entity: ApiKeyEntity): ApiKeyEntity
}