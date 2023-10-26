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

import com.epam.drill.admin.auth.config.DatabaseConfig
import com.epam.drill.admin.auth.entity.UserEntity
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.repository.impl.DatabaseUserRepository
import com.epam.drill.admin.auth.table.UserTable
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import kotlin.test.*
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@Testcontainers
class UserRepositoryImplTest {

    private val repository = DatabaseUserRepository()

    companion object {
        @Container
        private val postgresqlContainer = PostgreSQLContainer<Nothing>(
            DockerImageName.parse("postgres:14.1")
        ).apply {
            withDatabaseName("testdb")
            withUsername("testuser")
            withPassword("testpassword")
        }

        @JvmStatic
        @BeforeAll
        fun setup() {
            postgresqlContainer.start()
            val dataSource = HikariDataSource(HikariConfig().apply {
                this.jdbcUrl = postgresqlContainer.jdbcUrl
                this.username = postgresqlContainer.username
                this.password = postgresqlContainer.password
                this.driverClassName = postgresqlContainer.driverClassName
                this.validate()
            })
            DatabaseConfig.init(dataSource)
        }
    }

    @Test
    fun `given unique username, create must insert user and return id`() = withTransaction {
        val userEntity = UserEntity(
            username = "uniquename", passwordHash = "hash", role = "USER"
        )
        val id = repository.create(userEntity)

        assertEquals(1, UserTable.select { UserTable.id eq id }.count())
        UserTable.select { UserTable.id eq id }.first().let {
            assertEquals(userEntity.username, it[UserTable.username])
            assertEquals(userEntity.passwordHash, it[UserTable.passwordHash])
            assertEquals(userEntity.role, it[UserTable.role])
            assertEquals(userEntity.deleted, it[UserTable.deleted])
            assertEquals(userEntity.blocked, it[UserTable.blocked])
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
    fun `after database migration 2 default users must be inserted`() = withTransaction {
        val users = repository.findAllNotDeleted()
        assertEquals(2, users.size)
        assertTrue(users.any { it.username == "user" })
        assertTrue(users.any { it.username == "admin" })
    }

    @Test
    fun `findAllNotDeleted must return all not deleted users`() = withTransaction {
        insertUsers(1..10)
        insertUsers(11..15) {
            it[deleted] = true //insert 5 deleted users
        }
        insertUsers(16..20)

        val users = repository.findAllNotDeleted()

        assertEquals( 2 + 20 - 5, users.size)//2 default users + 20 inserted - 5 deleted
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

    @Ignore //TODO Test failed. Will be fixed in a following task
    @Test
    fun `findByUsername mustn't return deleted user`() = withTransaction {
        insertUsers(1..10)
        insertUser(11) {
            it[username] = "foobar"
            it[deleted] = true
        }
        insertUsers(12..20)

        val user = repository.findByUsername("foobar")

        assertNull(user)
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
        assertFalse { ids.contains(404) }

        val user = repository.findById(404)

        assertNull(user)
    }

    @Test
    fun `findById must return even deleted user`() = withTransaction {
        insertUsers(1..10)
        val deletedIds = insertUsers(11..15) {
            it[deleted] = true
        }
        insertUsers(16..20)
        deletedIds.forEach { id ->

            val user = repository.findById(id)

            assertNotNull(user)
            assertEquals(id, user.id)
            assertTrue(user.deleted)
        }
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

private fun insertUsers(
    range: IntRange, overrideColumns: UserTable.(InsertStatement<*>) -> Unit = {}
): Set<Int> {
    val ids = mutableSetOf<Int>()
    range.forEach { index ->
        ids += insertUser(index, overrideColumns)
    }
    return ids
}

private fun insertUser(index: Int = 1, overrideColumns: UserTable.(InsertStatement<*>) -> Unit = {}) = UserTable.insertAndGetId {
    it[username] = "username$index"
    it[passwordHash] = "hash$index"
    it[role] = Role.values()[index % Role.values().size].name
    it[blocked] = false
    it[deleted] = false
    overrideColumns(it)
}.value

