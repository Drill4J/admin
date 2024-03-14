package com.epam.drill.admin.writer.rawdata

import com.epam.drill.admin.writer.rawdata.config.BitSetColumnType
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig.transaction
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.util.*

class BitSetColumnTypeTest : DatabaseTests() {

    @BeforeEach
    fun initSchema() {
        withTransaction {
            create(BitSets)
        }
    }

    @Test
    fun `test storing and retrieving BitSet`() = withTransaction {
        val originalBitSet = BitSet().apply {
            set(0)
            set(2)
            set(5)
        }

        BitSets.insert {
            it[bits] = originalBitSet
        }
        val retrievedBitSet = BitSets.selectAll().single()[BitSets.bits]

        assertEquals(originalBitSet, retrievedBitSet)
    }

}

object BitSets : IntIdTable() {
    val bits = registerColumn<BitSet>("bits", BitSetColumnType())
}

@Testcontainers
open class DatabaseTests {

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
            RawDataWriterDatabaseConfig.init(dataSource)
        }

        @JvmStatic
        @AfterAll
        fun finish() {
            postgresqlContainer.stop()
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