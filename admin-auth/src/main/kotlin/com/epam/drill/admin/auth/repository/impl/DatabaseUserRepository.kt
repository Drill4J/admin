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
import com.epam.drill.admin.auth.repository.UserRepository
import com.epam.drill.admin.auth.table.UserTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder

class DatabaseUserRepository : UserRepository {
    override suspend fun findAllNotDeleted(): List<UserEntity> {
        return UserTable.select { UserTable.deleted eq false }.map { it.toEntity() }
    }

    override suspend fun findById(id: Int): UserEntity? {
        return UserTable.select { UserTable.id eq id }.map { it.toEntity() }.firstOrNull()
    }

    override suspend fun findByUsername(username: String): UserEntity? {
        return UserTable.select { UserTable.username.lowerCase() eq username.lowercase() }.map { it.toEntity() }.firstOrNull()
    }

    override suspend fun create(entity: UserEntity): Int {
        return UserTable.insertAndGetId { entity.mapTo(it) }.value
    }

    override suspend fun update(entity: UserEntity) {
        UserTable.update(
            where = { UserTable.id eq entity.id },
            body = { entity.mapTo(it) }
        )
    }
}

private fun ResultRow.toEntity() = UserEntity(
    id = this[UserTable.id].value,
    username = this[UserTable.username],
    passwordHash = this[UserTable.passwordHash],
    role = this[UserTable.role],
    blocked = this[UserTable.blocked],
    deleted = this[UserTable.deleted],
)

private fun UserEntity.mapTo(it: UpdateBuilder<Int>) {
    it[UserTable.username] = username
    it[UserTable.passwordHash] = passwordHash
    it[UserTable.role] = role
    it[UserTable.blocked] = blocked
    it[UserTable.deleted] = deleted
}