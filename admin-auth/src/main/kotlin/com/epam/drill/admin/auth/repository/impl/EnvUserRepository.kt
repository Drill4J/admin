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
import com.epam.drill.admin.auth.model.Role
import com.epam.drill.admin.auth.repository.UserRepository
import com.epam.drill.admin.auth.service.PasswordService
import io.ktor.config.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

class EnvUserRepository(
    private val env: ApplicationConfig,
    private val passwordService: PasswordService
) : UserRepository {

    private var users: MutableMap<Int, UserEntity>

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
            .associateBy { it.id!! }
            .toMap(ConcurrentHashMap())
    }

    override fun findAllNotDeleted(): List<UserEntity> {
        return users.values.filter { !it.deleted }.map(UserEntity::copy)
    }

    override fun findById(id: Int): UserEntity? {
        return users[id]?.copy()
    }

    override fun findByUsername(username: String): UserEntity? {
        return users[genId(username)]?.copy()
    }

    override fun create(entity: UserEntity): Int {
        val id = genId(entity.username)
        users[id] = entity.copy(id = id)
        return id
    }

    override fun update(entity: UserEntity) {
        users[entity.id!!] = entity.copy()
    }

    private fun getUsersFromEnv() = env.config("drill").propertyOrNull("users")?.getList() ?: emptyList()

    private fun UserConfig.toEntity(): UserEntity {
        return UserEntity(
            id = genId(username),
            username = username,
            passwordHash = passwordService.hashPassword(password),
            role = role.name)
    }

    private fun genId(username: String) = username.hashCode()
}
