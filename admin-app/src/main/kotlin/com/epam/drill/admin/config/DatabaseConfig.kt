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
package com.epam.drill.admin.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import io.ktor.server.config.*
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton
import javax.sql.DataSource

class DatabaseConfig(private val config: ApplicationConfig) {
    val host: String
        get() = config.propertyOrNull("host")?.getString() ?: "localhost"

    val port: Int
        get() = config.propertyOrNull("port")?.getString()?.toInt() ?: 5432

    val databaseName: String
        get() = config.propertyOrNull("dbName")?.getString() ?: "postgres"

    val username: String
        get() = config.propertyOrNull("userName")?.getString() ?: "postgres"

    val password: String
        get() = config.propertyOrNull("password")?.getString() ?: "postgres"

    val maxPoolSize: Int
        get() = config.propertyOrNull("maximumPoolSize")?.getString()?.toInt() ?: 50

    val ssl: Boolean
        get() = config.propertyOrNull("ssl")?.getString()?.toBooleanStrictOrNull() ?: false
}

private fun DatabaseConfig.toHikariConfig(): HikariConfig = HikariConfig().apply {
    this.driverClassName = "org.postgresql.Driver"
    this.jdbcUrl = "jdbc:postgresql://${host}:${port}/${databaseName}"
    this.username = this@toHikariConfig.username
    this.password = this@toHikariConfig.password
    this.maximumPoolSize = maxPoolSize
    this.isAutoCommit = true
    this.transactionIsolation = "TRANSACTION_READ_UNCOMMITTED"
    this.addDataSourceProperty("rewriteBatchedInserts", true)
    this.addDataSourceProperty("rewriteBatchedStatements", true)
    if (ssl) {
        this.addDataSourceProperty("ssl", true)
        this.addDataSourceProperty("sslmode", "require")
    }
    this.validate()
}

val dataSourceDIModule = DI.Module("dataSource") {
    bind<DatabaseConfig>() with singleton {
        DatabaseConfig(instance<Application>().environment.config.config("drill.database"))
    }
    bind<DatabaseConfig>(tag = "metrics") with singleton {
        DatabaseConfig(instance<Application>().environment.config.config("drill.metrics.database"))
    }
    bind<DataSource>() with singleton {
        HikariDataSource(instance<DatabaseConfig>().toHikariConfig())
    }
    bind<DataSource>(tag = "metrics") with singleton {
        val mainConfig = instance<DatabaseConfig>()
        val metricsConfig = instance<DatabaseConfig>(tag = "metrics")
        if (metricsConfig.host == mainConfig.host
            && metricsConfig.port == mainConfig.port
            && metricsConfig.databaseName == mainConfig.databaseName
            && metricsConfig.username == mainConfig.username
            && metricsConfig.password == mainConfig.password
        ) {
            instance<DataSource>()
        } else {
            HikariDataSource(metricsConfig.toHikariConfig())
        }
    }
}