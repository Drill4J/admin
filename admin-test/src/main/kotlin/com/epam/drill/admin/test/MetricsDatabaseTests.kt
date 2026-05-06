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
package com.epam.drill.admin.test

import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import javax.sql.DataSource

@Testcontainers
open class MetricsDatabaseTests(private val initialization: (default: DataSource, metrics: DataSource) -> Unit) {

    companion object {
        private const val METRICS_DB_NAME = "metrics_db"

        private lateinit var defaultDataSource: DataSource
        private lateinit var metricsDataSource: DataSource

        @Container
        private val postgresqlContainer: PostgreSQLContainer<Nothing> =
            newPostgresContainer(initScript = "metrics_db_init.sql")

        @JvmStatic
        @BeforeAll
        fun setup() {
            postgresqlContainer.start()
            defaultDataSource = postgresqlContainer.newHikariDataSource()
            metricsDataSource = postgresqlContainer.newHikariDataSource(databaseName = METRICS_DB_NAME)
        }

        @JvmStatic
        @AfterAll
        fun finish() {
            (defaultDataSource as HikariDataSource).close()
            (metricsDataSource as HikariDataSource).close()
            postgresqlContainer.stop()
        }
    }

    @BeforeEach
    fun initDatabase() {
        initialization(defaultDataSource, metricsDataSource)
    }
}