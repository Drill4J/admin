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
package com.epam.drill.admin.auth.repository.impl

import com.epam.drill.admin.auth.entity.ApiKeyEntity
import com.epam.drill.admin.auth.entity.UserEntity
import com.epam.drill.admin.auth.repository.ApiKeyRepository
import com.epam.drill.admin.auth.table.ApiKeyTable
import com.epam.drill.admin.auth.table.UserTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.UpdateBuilder

class DatabaseApiKeyRepository: ApiKeyRepository {
    override suspend fun findAll(): List<ApiKeyEntity> {
        return (ApiKeyTable innerJoin UserTable).selectAll().map { it.toEntity() }
    }

    override suspend fun findAllByUserId(userId: Int): List<ApiKeyEntity> {
        return ApiKeyTable.select { ApiKeyTable.userId eq userId }.map { it.toEntity() }
    }

    override suspend fun findById(id: Int): ApiKeyEntity? {
        return (ApiKeyTable innerJoin UserTable).select { ApiKeyTable.id eq id }.map { it.toEntity() }.firstOrNull()
    }

    override suspend fun deleteById(id: Int) {
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
    createdAt = this[ApiKeyTable.createdAt],
    user = this.hasValue(UserTable.username).takeIf { it }?.let {
        UserEntity(
            id = this[ApiKeyTable.userId],
            username = this[UserTable.username],
            passwordHash = this[UserTable.passwordHash],
            role = this[UserTable.role],
            blocked = this[UserTable.blocked],
            registrationDate = this[UserTable.registrationDate]
        )
    },
)

private fun ApiKeyEntity.mapTo(builder: UpdateBuilder<Int>) {
    builder[ApiKeyTable.description] = description
    builder[ApiKeyTable.apiKeyHash] = apiKeyHash
    builder[ApiKeyTable.userId] = userId
    builder[ApiKeyTable.expiresAt] = expiresAt
    builder[ApiKeyTable.createdAt] = createdAt
}