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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import java.time.LocalDateTime

class DatabaseUserRepository(
    val currentDateTimeProvider: () -> LocalDateTime = { LocalDateTime.now() }
) : UserRepository {
    override suspend fun findAll(): List<UserEntity> {
        return UserTable.selectAll().map { it.toEntity() }
    }

    override suspend fun findById(id: Int): UserEntity? {
        return UserTable.select { UserTable.id eq id }.map { it.toEntity() }.firstOrNull()
    }

    override suspend fun findByUsername(username: String): UserEntity? {
        return UserTable.select { UserTable.username.lowerCase() eq username.lowercase() }.map { it.toEntity() }
            .firstOrNull()
    }

    override suspend fun create(entity: UserEntity): UserEntity {
        return UserTable.insertAndGetId { entity.mapTo(it) }.value.let { entity.copy(id = it) }
    }

    override suspend fun update(entity: UserEntity): UserEntity {
        UserTable.update(
            where = { UserTable.id eq entity.id },
            body = { entity.mapTo(it) }
        )
        return entity
    }

    override suspend fun deleteById(id: Int) {
        UserTable.deleteWhere { UserTable.id eq id }
    }

    private fun ResultRow.toEntity() = UserEntity(
        id = this[UserTable.id].value,
        username = this[UserTable.username],
        passwordHash = this[UserTable.passwordHash],
        role = this[UserTable.role],
        blocked = this[UserTable.blocked],
        registrationDate = this[UserTable.registrationDate]
    )

    private fun UserEntity.mapTo(builder: UpdateBuilder<Int>) {
        builder[UserTable.username] = username
        builder[UserTable.passwordHash] = passwordHash
        builder[UserTable.role] = role
        builder[UserTable.blocked] = blocked
        builder[UserTable.registrationDate] = registrationDate ?: currentDateTimeProvider()
    }
}