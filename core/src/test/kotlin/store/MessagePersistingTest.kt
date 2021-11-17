/**
 * Copyright 2020 EPAM Systems
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
package com.epam.drill.admin.store

import com.epam.dsm.*
import com.zaxxer.hikari.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.*
import org.testcontainers.containers.*
import kotlin.test.*
import kotlin.test.Test

class MessagePersistingTest {

    @Serializable
    data class SimpleMessage(val s: String)

    @Test
    fun `storeMessage - readMessage`() {
        val storeClient = pluginStoresDSM("${MessagePersistingTest::class.simpleName}")
        val message = SimpleMessage("data")
        runBlocking {
            assertNull(storeClient.readMessage("1"))
            storeClient.storeMessage("1", message)
            assertEquals(message, storeClient.readMessage("1"))
        }
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun connectDB() {
            val port = 5432
            val dbName = "dbName"
            val postgresContainer = PostgreSQLContainer<Nothing>("postgres:12").apply {
                withDatabaseName(dbName)
                withExposedPorts(port)
                start()
            }
            println("started container with id ${postgresContainer.containerId}.")
            Thread.sleep(5000) //todo :) timeout
            DatabaseFactory.init(HikariDataSource(HikariConfig().apply {
                this.driverClassName = "org.postgresql.Driver"
                this.jdbcUrl =
                    "jdbc:postgresql://${postgresContainer.host}:${postgresContainer.getMappedPort(port)}/$dbName"
                this.username = postgresContainer.username
                this.password = postgresContainer.password
                this.maximumPoolSize = 3
                this.isAutoCommit = false
                this.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                this.validate()
            }))
        }
    }


}
