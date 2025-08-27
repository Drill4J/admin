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

import com.epam.drill.admin.auth.entity.UserEntity
import com.epam.drill.admin.common.principal.Role
import com.epam.drill.admin.auth.repository.impl.DatabaseUserRepository
import com.epam.drill.admin.auth.table.UserTable
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.LocalDateTime
import java.time.Month
import kotlin.test.*

class DatabaseUserRepositoryTest: DatabaseTests() {

    private val repository = DatabaseUserRepository()

    @Test
    fun `given unique username, create must insert user and return user entity with id`() = withTransaction {
        val userEntity = UserEntity(
            username = "uniquename",
            passwordHash = "hash",
            role = "USER",
            registrationDate = LocalDateTime.of(2023, Month.JANUARY, 1, 0, 0)
        )
        val createdUserEntity = repository.create(userEntity)

        assertEquals(1, UserTable.selectAll().where { UserTable.id eq createdUserEntity.id }.count())
        UserTable.selectAll().where { UserTable.id eq createdUserEntity.id }.first().let {
            assertEquals(userEntity.username, it[UserTable.username])
            assertEquals(userEntity.passwordHash, it[UserTable.passwordHash])
            assertEquals(userEntity.role, it[UserTable.role])
            assertEquals(userEntity.blocked, it[UserTable.blocked])
            assertEquals(userEntity.registrationDate, it[UserTable.registrationDate])
        }
    }

    @Test
    fun `given not specified registration date, create must insert user and return registration date issued by date time provider`() =
        withTransaction {
            val userEntity = UserEntity(
                username = "somename",
                passwordHash = "hash",
                role = "USER"
            )
            val currentDateTimeStub = LocalDateTime.of(2023, Month.JANUARY, 1, 0, 0)
            val repository = DatabaseUserRepository(currentDateTimeProvider = { currentDateTimeStub })

            val createdUserEntity = repository.create(userEntity)

            assertEquals(1, UserTable.selectAll().where { UserTable.id eq createdUserEntity.id }.count())
            UserTable.selectAll().where { UserTable.id eq createdUserEntity.id }.first().let {
                assertEquals(it[UserTable.registrationDate], currentDateTimeStub)
            }
        }

    @Test
    fun `given non-unique username, create must fail`() = withTransaction {
        insertUser {
            it[username] = "nonuniquename"
        }

        assertFails {
            repository.create(
                UserEntity(
                    username = "nonuniquename", passwordHash = "hash", role = "USER"
                )
            )
        }
    }

    @Test
    fun `given case insensitive username, create must fail`() = withTransaction {
        insertUser {
            it[username] = "FooBar"
        }

        assertFails {
            repository.create(
                UserEntity(
                    username = "foobar", passwordHash = "hash", role = "USER"
                )
            )
        }
    }

    @Test
    fun `given existing id, update must change fields for exactly one user`() = withTransaction {
        insertUsers(1..10)
        val id = insertUser(11) {
            it[username] = "foo"
            it[passwordHash] = "hash1"
            it[role] = "USER"
        }
        insertUsers(12..20)

        repository.update(
            UserEntity(
                id = id, username = "bar", passwordHash = "hash2", role = "ADMIN", blocked = true
            )
        )

        assertEquals(1, UserTable.selectAll().where { UserTable.username eq "bar" }.count())
        UserTable.selectAll().where { UserTable.username eq "bar" }.first().let {
            assertEquals("bar", it[UserTable.username])
            assertEquals("hash2", it[UserTable.passwordHash])
            assertEquals("ADMIN", it[UserTable.role])
            assertTrue(it[UserTable.blocked])
        }
    }

    @Test
    fun `after database migration, findAll must return default users`() = withTransaction {
        val users = repository.findAll()

        assertEquals(2, users.size)//insert after db migration
        assertTrue(users.any { it.username == "user" })
        assertTrue(users.any { it.username == "admin" })
    }

    @Test
    fun `given existing username, findByUsername must return user`() = withTransaction {
        insertUsers(1..10)
        insertUser(11) {
            it[username] = "foobar"
        }
        insertUsers(12..20)

        val user = repository.findByUsername("foobar")

        assertNotNull(user)
        assertEquals("foobar", user.username)
    }

    @Test
    fun `findByUsername must return even blocked user`() = withTransaction {
        insertUsers(1..10)
        insertUser(11) {
            it[username] = "foobar"
            it[blocked] = true
        }
        insertUsers(12..20)

        val user = repository.findByUsername("foobar")

        assertNotNull(user)
        assertEquals("foobar", user.username)
        assertTrue(user.blocked)
    }

    @Test
    fun `findByUsername must be case insensitive`() = withTransaction {
        insertUsers(1..10)
        insertUser(11) {
            it[username] = "FooBar"
        }
        insertUsers(12..20)

        val user = repository.findByUsername("foobar")

        assertNotNull(user)
        assertEquals("FooBar", user.username)
    }

    @Test
    fun `given existing id, findById must return user`() = withTransaction {
        val ids = insertUsers(1..10)
        ids.forEach { id ->

            val user = repository.findById(id)

            assertNotNull(user)
            assertEquals(id, user.id)
        }
    }

    @Test
    fun `given non-existent id, findById must return null`() = withTransaction {
        val ids = insertUsers(1..10)
        assertFalse { ids.contains(12345) }

        val user = repository.findById(12345)

        assertNull(user)
    }

    @Test
    fun `given existent id, delete must delete user`() = withTransaction {
        val id = insertUser {
            it[username] = "foo"
        }

        repository.deleteById(id)

        assertEquals(0, UserTable.selectAll().where { UserTable.id eq id }.count())
    }

    @Test
    fun `given non-existent id, delete must not fail`() = withTransaction {
        assertDoesNotThrow {
            repository.deleteById(12345)
        }
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

private fun insertUsers(
    range: IntRange, overrideColumns: UserTable.(InsertStatement<*>) -> Unit = {}
): Set<Int> {
    val ids = mutableSetOf<Int>()
    range.forEach { index ->
        ids += insertUser(index, overrideColumns)
    }
    return ids
}

private fun insertUser(index: Int = 1, overrideColumns: UserTable.(InsertStatement<*>) -> Unit = {}) =
    UserTable.insertAndGetId {
        it[username] = "username$index"
        it[passwordHash] = "hash$index"
        it[role] = Role.values()[index % Role.values().size].name
        it[blocked] = false
        it[registrationDate] = LocalDateTime.now()
        overrideColumns(it)
    }.value

