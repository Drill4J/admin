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

import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig
import com.epam.drill.admin.writer.rawdata.config.rawDataServicesDIModule
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.serialization.kotlinx.json.*
import io.ktor.serialization.kotlinx.protobuf.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.resources.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.kodein.di.ktor.di
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertEquals

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

fun withRollback(test: suspend () -> Unit) {
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


fun dataIngestApplication(routing: TestApplicationBuilder.() -> Unit) = TestApplication {
    install(Resources)
    install(ContentNegotiation) {
        json()
        protobuf()
    }
    application {
        di {
            import(rawDataServicesDIModule)
        }
    }
    routing()
}

fun assertJsonEquals(json1: String, json2: String) {
    val json = Json { ignoreUnknownKeys = true }

    val obj1: JsonElement = json.parseToJsonElement(json1)
    val obj2: JsonElement = json.parseToJsonElement(json2)

    assertEquals(obj1, obj2)
}