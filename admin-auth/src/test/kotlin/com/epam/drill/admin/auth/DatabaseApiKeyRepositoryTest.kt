package com.epam.drill.admin.auth

import com.epam.drill.admin.auth.config.DatabaseConfig
import com.epam.drill.admin.auth.entity.ApiKeyEntity
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.repository.impl.DatabaseApiKeyRepository
import com.epam.drill.admin.auth.table.ApiKeyTable
import com.epam.drill.admin.auth.table.UserTable
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.LocalDateTime
import java.time.Month
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Testcontainers
class DatabaseApiKeyRepositoryTest {
    private val repository = DatabaseApiKeyRepository()

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

        @JvmStatic
        @AfterAll
        fun finish() {
            postgresqlContainer.stop()
        }
    }

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

        assertEquals(1, ApiKeyTable.select { ApiKeyTable.id eq createdApiKeyEntity.id }.count())
    }

    @Test
    fun `given userId, getAllByUserId must return all api-keys of this user`() = withTransaction {
        val userId1 = insertUser(1)
        val userId2 = insertUser(2)
        insertApiKey { it[userId] = userId1 }
        insertApiKey { it[userId] = userId1 }
        insertApiKey { it[userId] = userId2 }

        val apiKeysByUserId1 = repository.getAllByUserId(userId1)
        val apiKeysByUserId2 = repository.getAllByUserId(userId2)

        assertEquals(2, apiKeysByUserId1.size)
        assertEquals(1, apiKeysByUserId2.size)
    }

    @Test
    fun `getAll must return all api-keys of all users`() = withTransaction {
        val userId1 = insertUser(1)
        val userId2 = insertUser(2)
        insertApiKey { it[userId] = userId1 }
        insertApiKey { it[userId] = userId1 }
        insertApiKey { it[userId] = userId2 }

        val allApiKeys = repository.getAll()

        assertEquals(3, allApiKeys.size)
    }

    @Test
    fun `given api-key id, delete must remove this api-key`() = withTransaction {
        val testUserId = insertUser()
        val testApiKeyId = insertApiKey { it[userId] = testUserId }

        repository.deleteById(testApiKeyId)

        assertEquals(0, ApiKeyTable.select { ApiKeyTable.id eq testApiKeyId }.count())
    }


    @Test
    fun `getAll must return not null user entity in every api-key entity`() = withTransaction {
        val testUserId = insertUser()
        insertApiKey { it[userId] = testUserId }
        insertApiKey { it[userId] = testUserId }

        val allApiKeys = repository.getAll()

        assertTrue(allApiKeys.all { it.user != null })
    }

    @Test
    fun `getAllByUserId must return null user entity in every api-key entity`() = withTransaction {
        val testUserId = insertUser()
        insertApiKey { it[userId] = testUserId }
        insertApiKey { it[userId] = testUserId }

        val apiKeys = repository.getAllByUserId(testUserId)

        assertTrue(apiKeys.all { it.user == null })
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

