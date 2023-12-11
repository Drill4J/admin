package com.epam.drill.admin.auth.repository.impl

import com.epam.drill.admin.auth.entity.ApiKeyEntity
import com.epam.drill.admin.auth.entity.UserEntity
import com.epam.drill.admin.auth.repository.ApiKeyRepository
import com.epam.drill.admin.auth.table.ApiKeyTable
import com.epam.drill.admin.auth.table.UserTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder

class DatabaseApiKeyRepository: ApiKeyRepository {
    override suspend fun getAll(): List<ApiKeyEntity> {
        return (ApiKeyTable innerJoin UserTable).selectAll().map { it.toEntity() }
    }

    override suspend fun getAllByUserId(userId: Int): List<ApiKeyEntity> {
        return ApiKeyTable.select { ApiKeyTable.userId eq userId }.map { it.toEntity() }
    }

    override suspend fun delete(id: Int) {
        ApiKeyTable.deleteWhere { ApiKeyTable.id eq id }
    }

    override suspend fun create(entity: ApiKeyEntity): ApiKeyEntity {
        return ApiKeyTable.insertAndGetId { entity.mapTo(it) }.value.let { entity.copy(id = it) }
    }
}

private fun ResultRow.toEntity() = ApiKeyEntity(
    id = this[ApiKeyTable.id].value,
    userId = this[ApiKeyTable.userId],
    description = this[ApiKeyTable.description],
    apiKeyHash = this[ApiKeyTable.apiKeyHash],
    expiresAt = this[ApiKeyTable.expiresAt],
    createdAt = this[ApiKeyTable.createdAt]
)

private fun ApiKeyEntity.mapTo(builder: UpdateBuilder<Int>) {
    builder[ApiKeyTable.description] = description
    builder[ApiKeyTable.apiKeyHash] = apiKeyHash
    builder[ApiKeyTable.userId] = userId
    builder[ApiKeyTable.expiresAt] = expiresAt
    builder[ApiKeyTable.createdAt] = createdAt
}