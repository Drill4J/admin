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
package com.epam.drill.admin.auth

import com.epam.drill.admin.auth.entity.ApiKeyEntity
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.repository.impl.DatabaseApiKeyRepository
import com.epam.drill.admin.auth.table.ApiKeyTable
import com.epam.drill.admin.auth.table.UserTable
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.Month
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DatabaseApiKeyRepositoryTest: DatabaseTests() {
    private val repository = DatabaseApiKeyRepository()

    @Test
    fun `given api-key entity, create must insert api-key and return api-key entity with id`() = withTransaction {
        val testUserId = insertUser()
        val apiKeyEntity = ApiKeyEntity(
            description = "for testing",
            apiKeyHash = "hash",
            userId = testUserId,
            expiresAt = LocalDateTime.of(2024, Month.JANUARY, 1, 0, 0),
            createdAt = LocalDateTime.of(2023, Month.JANUARY, 1, 0, 0)
        )

        val createdApiKeyEntity = repository.create(apiKeyEntity)

        assertEquals(1, ApiKeyTable.selectAll().where { (ApiKeyTable.id eq createdApiKeyEntity.id) and (ApiKeyTable.userId eq testUserId) }.count())
    }

    @Test
    fun `given userId, getAllByUserId must return all api-keys of this user`() = withTransaction {
        val userId1 = insertUser(1)
        val userId2 = insertUser(2)
        insertApiKey { it[userId] = userId1 }
        insertApiKey { it[userId] = userId1 }
        insertApiKey { it[userId] = userId2 }

        val apiKeysByUserId1 = repository.findAllByUserId(userId1)
        val apiKeysByUserId2 = repository.findAllByUserId(userId2)

        assertEquals(2, apiKeysByUserId1.size)
        assertTrue(apiKeysByUserId1.all { it.userId == userId1 })
        assertEquals(1, apiKeysByUserId2.size)
        assertTrue(apiKeysByUserId2.all { it.userId == userId2 })
    }

    @Test
    fun `getAll must return all api-keys of all users`() = withTransaction {
        val userId1 = insertUser(1)
        val userId2 = insertUser(2)
        insertApiKey { it[userId] = userId1 }
        insertApiKey { it[userId] = userId1 }
        insertApiKey { it[userId] = userId2 }

        val allApiKeys = repository.findAll()

        assertEquals(3, allApiKeys.size)
    }

    @Test
    fun `given api-key id, delete must remove this api-key`() = withTransaction {
        val testUserId = insertUser()
        val testApiKeyId = insertApiKey { it[userId] = testUserId }

        repository.deleteById(testApiKeyId)

        assertEquals(0, ApiKeyTable.selectAll().where { ApiKeyTable.id eq testApiKeyId }.count())
    }


    @Test
    fun `getAll must return not null user entity in every api-key entity`() = withTransaction {
        val testUserId = insertUser()
        insertApiKey { it[userId] = testUserId }
        insertApiKey { it[userId] = testUserId }

        val allApiKeys = repository.findAll()

        assertTrue(allApiKeys.all { it.user != null })
    }

    @Test
    fun `getAllByUserId must return null user entity in every api-key entity`() = withTransaction {
        val testUserId = insertUser()
        insertApiKey { it[userId] = testUserId }
        insertApiKey { it[userId] = testUserId }

        val apiKeys = repository.findAllByUserId(testUserId)

        assertTrue(apiKeys.all { it.user == null })
    }

    @Test
    fun `given existing api key identifier, getByIdAll must return api key entity with non null user`() = withTransaction {
        val testUserId = insertUser()
        val testApiKeyId = insertApiKey { it[userId] = testUserId }

        val apiKey = repository.findById(testApiKeyId)

        assertNotNull(apiKey)
        assertNotNull(apiKey.user)
    }
}

private fun withTransaction(test: suspend () -> Unit) {
    runBlocking {
        newSuspendedTransaction {
            try {
                test()
            } finally {
                rollback()
            }
        }
    }
}

private fun insertApiKey(overrideColumns: ApiKeyTable.(InsertStatement<*>) -> Unit = {}) =
    ApiKeyTable.insertAndGetId {
        it[description] = "for testing"
        it[apiKeyHash] = "hash"
        it[expiresAt] = LocalDateTime.now().plusYears(1)
        it[createdAt] = LocalDateTime.now()
        overrideColumns(it)
    }.value

private fun insertUser(index: Int = 1, overrideColumns: UserTable.(InsertStatement<*>) -> Unit = {}) =
    UserTable.insertAndGetId {
        it[username] = "apikey-username$index"
        it[passwordHash] = "hash$index"
        it[role] = Role.values()[index % Role.values().size].name
        it[blocked] = false
        it[registrationDate] = LocalDateTime.now()
        overrideColumns(it)
    }.value

