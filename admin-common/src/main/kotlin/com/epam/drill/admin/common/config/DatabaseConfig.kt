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
package com.epam.drill.admin.common.config

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import javax.sql.DataSource
import org.flywaydb.core.Flyway

open class DatabaseConfig(
    private val dbSchema: String,
    private val schemaMigrationLocation: String,

    ) {
    private lateinit var database: Database
    private var dispatcher: CoroutineDispatcher = Dispatchers.IO


    fun init(
        dataSource: DataSource,
        defaultMaxAttempts: Int = 3
    ) {
        this.database = Database.connect(
            datasource = dataSource,
            databaseConfig = org.jetbrains.exposed.sql.DatabaseConfig {
                this.defaultMaxAttempts = defaultMaxAttempts
            }
        )
        Flyway.configure()
            .dataSource(dataSource)
            .schemas(dbSchema)
            .baselineOnMigrate(true)
            .locations(schemaMigrationLocation)
            .load()
            .migrate()
    }

    suspend fun <T> transaction(block: suspend Transaction.() -> T): T =
        newSuspendedTransaction(dispatcher, database) { block() }
}