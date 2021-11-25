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
package com.epam.drill.e2e

import com.epam.drill.admin.plugins.*
import com.epam.drill.admin.store.*
import com.epam.drill.testdata.*
import com.epam.dsm.*
import com.zaxxer.hikari.*
import org.jetbrains.exposed.sql.transactions.*
import org.testcontainers.containers.*
import org.testcontainers.containers.wait.strategy.*

class TestDatabaseContainer {
    companion object {
        var isNeedStartContainer = true

        fun startOnce() {
            if (isNeedStartContainer) {
                isNeedStartContainer = false
                start()
            }
        }

        private fun start(): PostgreSQLContainer<Nothing> {
            println("embedded postgres...")
            val postgresContainer = PostgreSQLContainer<Nothing>("postgres:12").apply {
                withDatabaseName("dbName")
                withExposedPorts(PostgreSQLContainer.POSTGRESQL_PORT)
                waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2))
                start()
            }
            println("started container with id ${postgresContainer.containerId}.")
            DatabaseFactory.init(HikariDataSource(HikariConfig().apply {
                this.driverClassName = postgresContainer.driverClassName
                this.jdbcUrl = postgresContainer.jdbcUrl
                this.username = postgresContainer.username
                this.password = postgresContainer.password
                this.maximumPoolSize = 3
                this.isAutoCommit = false
                this.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                this.validate()
            }))
            return postgresContainer
        }

        fun clearData() {
            println("clear database...")
            transaction {
                exec("DROP SCHEMA IF EXISTS global CASCADE")
                exec("DROP SCHEMA IF EXISTS ${commonStore.schema} CASCADE")
                exec("DROP SCHEMA IF EXISTS ${agentStores.schema} CASCADE")
                exec("DROP SCHEMA IF EXISTS ${pluginStoresDSM(TEST2CODE).schema} CASCADE")
                exec("DROP SCHEMA IF EXISTS ${pluginStoresDSM(pluginId).schema} CASCADE")
            }
        }
    }
}
