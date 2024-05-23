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
package com.epam.drill.admin.writer.rawdata

import com.epam.drill.admin.writer.rawdata.config.ProbesColumnType
import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig
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

class ProbesColumnTypeTest : DatabaseTests() {

    @BeforeEach
    fun initSchema() {
        withTransaction {
            create(BoolArrays)
        }
    }

    @Test
    fun `test storing and retrieving Probes`() = withTransaction {
        val originalProbes = arrayOf(true, false, true, false, false, true)

        BoolArrays.insert {
            it[boolArrays] = originalProbes
        }
        val retrievedProbes = BoolArrays.selectAll().single()[BoolArrays.boolArrays]

        assertEquals(originalProbes, retrievedProbes)
    }

}

object BoolArrays : IntIdTable() {
    val boolArrays = registerColumn<Array<Boolean>>("bool_arrays", ProbesColumnType())
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