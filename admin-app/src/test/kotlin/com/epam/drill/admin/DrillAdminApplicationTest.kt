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
package com.epam.drill.admin

import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.application.*
import io.ktor.config.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import kotlin.test.Test
import kotlin.test.assertEquals

class DrillAdminApplicationTest: DatabaseTests() {
    @Test
    fun `ui-config route should respond with 200 OK`() {
        withTestApplication({
            dbSetup()
            module()
        }) {
            handleRequest(HttpMethod.Get, "/api/ui-config").apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    @Test
    fun `root route should respond with 200 OK`() {
        withTestApplication({
            dbSetup()
            module()
        }) {
            handleRequest(HttpMethod.Get, "/").apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    private fun Application.dbSetup() {
        (this.environment.config as MapApplicationConfig).apply {
            put("drill.database.host", postgresqlContainer.host)
            put(
                "drill.database.port",
                postgresqlContainer.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT).toString()
            )
            put("drill.database.dbName", postgresqlContainer.databaseName)
            put("drill.database.userName", postgresqlContainer.username)
            put("drill.database.password", postgresqlContainer.password)
        }
    }
}

@Testcontainers
open class DatabaseTests {

    companion object {
        @Container
        val postgresqlContainer = PostgreSQLContainer<Nothing>(
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
