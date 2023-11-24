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

import com.epam.drill.admin.auth.entity.UserEntity
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.repository.UserRepository
import com.epam.drill.admin.auth.service.PasswordService
import io.ktor.config.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class EnvUserRepository(
    private val env: ApplicationConfig,
    private val passwordService: PasswordService
) : UserRepository {

    private var users: Map<Int, UserEntity>

    @Serializable
    private data class UserConfig(
        val username: String,
        val password: String,
        val role: Role
    )

    init {
        users = getUsersFromEnv()
            .map { Json.decodeFromString(UserConfig.serializer(), it) }
            .map { it.toEntity() }
            .associateBy { it.id!! } //id cannot be null because is always filled by toEntity() function
            .toMap()
    }

    override suspend fun findAll(): List<UserEntity> {
        return users.values.toList()
    }

    override suspend fun findById(id: Int): UserEntity? {
        return users[id]
    }

    override suspend fun findByUsername(username: String): UserEntity? {
        return users[genId(username.lowercase())]
    }

    override suspend fun create(entity: UserEntity): UserEntity {
        throw UnsupportedOperationException("User creation is not supported")
    }

    override suspend fun update(entity: UserEntity): UserEntity {
        throw UnsupportedOperationException("User update is not supported")
    }

    override suspend fun deleteById(id: Int) {
        throw UnsupportedOperationException("User deletion is not supported")
    }

    private fun getUsersFromEnv() = env
        .config("drill")
        .config("auth")
        .propertyOrNull("envUsers")?.getList() ?: emptyList()

    private fun UserConfig.toEntity(): UserEntity {
        return UserEntity(
            id = genId(username.lowercase()),
            username = this.username,
            passwordHash = passwordService.hashPassword(password),
            role = role.name)
    }

    private fun genId(username: String) = username.hashCode()
}
