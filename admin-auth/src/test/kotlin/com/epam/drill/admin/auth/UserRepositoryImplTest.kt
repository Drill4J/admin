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
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.repository.impl.UserRepositoryImpl
import com.epam.drill.admin.auth.table.UserTable
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.*

class UserRepositoryImplTest {

    private val repository = UserRepositoryImpl()

    @BeforeTest
    fun setup() {
        Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "org.h2.Driver", user = "root", password = "")
        transaction {
            SchemaUtils.createSchema(Schema("auth"))
            SchemaUtils.create(UserTable)
        }
    }

    @Test
    fun `given unique username, create must insert user and return id`() = withTransaction {
        val id = repository.create(
            UserEntity(
                username = "uniquename", passwordHash = "hash", role = "USER"
            )
        )

        assertEquals(1, UserTable.select { UserTable.id eq id }.count())
        UserTable.select { UserTable.id eq id }.first().let {
            assertEquals("uniquename", it[UserTable.username])
            assertEquals("hash", it[UserTable.passwordHash])
            assertEquals("USER", it[UserTable.role])
            assertFalse(it[UserTable.deleted])
            assertFalse(it[UserTable.blocked])
        }
    }

    @Ignore //TODO Test failed. To solve this need to use postgres and migration scripts.
    @Test
    fun `given non-unique username, create must fail`() = withTransaction {
        insertRandomUsers(1..1) {
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

    @Ignore //TODO Test failed. To solve this need to use postgres and migration scripts.
    @Test
    fun `given case insensitive username, create must fail`() = withTransaction {
        insertRandomUsers(1..1) {
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
    fun `given existing id, update must change user`() = withTransaction {
        insertRandomUsers(1..10)
        val id = insertRandomUsers(11..11) {
            it[username] = "foo"
            it[passwordHash] = "hash1"
            it[role] = "USER"
        }.first()
        insertRandomUsers(12..20)

        repository.update(
            UserEntity(
                id = id, username = "bar", passwordHash = "hash2", role = "ADMIN", deleted = true, blocked = true
            )
        )

        assertEquals(1, UserTable.select { UserTable.username eq "bar" }.count())
        UserTable.select { UserTable.username eq "bar" }.first().let {
            assertEquals("bar", it[UserTable.username])
            assertEquals("hash2", it[UserTable.passwordHash])
            assertEquals("ADMIN", it[UserTable.role])
            assertTrue(it[UserTable.deleted])
            assertTrue(it[UserTable.blocked])
        }
    }

    @Test
    fun `findAllNotDeleted must return all not deleted users`() = withTransaction {
        insertRandomUsers(1..10)
        insertRandomUsers(11..15) {
            it[deleted] = true //insert 5 deleted users
        }
        insertRandomUsers(16..20)

        val users = repository.findAllNotDeleted()

        assertEquals(20 - 5, users.size)
    }

    @Test
    fun `given existing username, findByUsername must return user`() = withTransaction {
        insertRandomUsers(1..10)
        insertRandomUsers(11..11) {
            it[username] = "foobar"
        }
        insertRandomUsers(12..20)

        val user = repository.findByUsername("foobar")

        assertNotNull(user)
        assertEquals("foobar", user.username)
    }

    @Test
    fun `findByUsername must return even blocked user`() = withTransaction {
        insertRandomUsers(1..10)
        insertRandomUsers(11..11) {
            it[username] = "foobar"
            it[blocked] = true
        }
        insertRandomUsers(12..20)

        val user = repository.findByUsername("foobar")

        assertNotNull(user)
        assertEquals("foobar", user.username)
        assertTrue(user.blocked)
    }

    @Ignore //TODO Test failed. Will be fixed in a following task
    @Test
    fun `findByUsername mustn't return deleted user`() = withTransaction {
        insertRandomUsers(1..10)
        insertRandomUsers(11..11) {
            it[username] = "foobar"
            it[deleted] = true
        }
        insertRandomUsers(12..20)

        val user = repository.findByUsername("foobar")

        assertNull(user)
    }

    @Test
    fun `given case insensitive username, findByUsername must return user`() = withTransaction {
        insertRandomUsers(1..10)
        insertRandomUsers(11..11) {
            it[username] = "FooBar"
        }
        insertRandomUsers(12..20)

        val user = repository.findByUsername("foobar")

        assertNotNull(user)
        assertEquals("FooBar", user.username)
    }

    @Test
    fun `given existing id, findById must return user`() = withTransaction {
        val id = insertRandomUsers(1..10).random()

        val user = repository.findById(id)

        assertNotNull(user)
        assertEquals(id, user.id)
    }

    @Test
    fun `given non-existent id, findById must return null`() = withTransaction {
        val ids = insertRandomUsers(1..10)
        assertFalse { ids.contains(404) }

        val user = repository.findById(404)

        assertNull(user)
    }

    @Test
    fun `findById must return even deleted user`() = withTransaction {
        insertRandomUsers(1..10)
        val id = insertRandomUsers(11..15) {
            it[deleted] = true
        }.random()
        insertRandomUsers(16..20)


        val user = repository.findById(id)

        assertNotNull(user)
        assertEquals(id, user.id)
        assertTrue(user.deleted)
    }
}

private fun withTransaction(test: suspend () -> Unit) {
    runBlocking {
        newSuspendedTransaction {
            test()
            rollback()
        }
    }
}

private fun insertRandomUsers(
    range: IntRange, overrideColumns: UserTable.(InsertStatement<*>) -> Unit = {}
): Set<Int> {
    val ids = mutableSetOf<Int>()
    range.forEach { index ->
        ids += UserTable.insertAndGetId {
            it[username] = "username$index"
            it[passwordHash] = "hash$index"
            it[role] = Role.values()[index % Role.values().size].name
            it[blocked] = false
            it[deleted] = false
            overrideColumns(it)
        }.value
    }
    return ids
}

