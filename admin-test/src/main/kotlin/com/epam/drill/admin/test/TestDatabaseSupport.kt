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

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

internal const val POSTGRES_IMAGE = "postgres:14.1"
internal const val TEST_DB_NAME = "testdb"
internal const val TEST_DB_USER = "testuser"
internal const val TEST_DB_PASSWORD = "testpassword"
internal const val TEST_DB_POOL_SIZE = 50

/**
 * Creates a PostgreSQL test container preconfigured with shared test credentials.
 * Optional [initScript] is applied as a Testcontainers init script (classpath resource).
 */
internal fun newPostgresContainer(initScript: String? = null): PostgreSQLContainer<Nothing> =
    PostgreSQLContainer<Nothing>(DockerImageName.parse(POSTGRES_IMAGE)).apply {
        withDatabaseName(TEST_DB_NAME)
        withUsername(TEST_DB_USER)
        withPassword(TEST_DB_PASSWORD)
        initScript?.let { withInitScript(it) }
    }

/**
 * Builds a Hikari pool against this container. If [databaseName] differs from the
 * container's primary database, the JDBC URL's database segment is replaced.
 */
internal fun PostgreSQLContainer<*>.newHikariDataSource(
    databaseName: String = this.databaseName
): HikariDataSource {
    val url = if (databaseName == this.databaseName) jdbcUrl
    else jdbcUrl.replace("/${this.databaseName}", "/$databaseName")
    return HikariDataSource(HikariConfig().apply {
        this.jdbcUrl = url
        this.username = this@newHikariDataSource.username
        this.password = this@newHikariDataSource.password
        this.driverClassName = this@newHikariDataSource.driverClassName
        this.maximumPoolSize = TEST_DB_POOL_SIZE
        validate()
    })
}

