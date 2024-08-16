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
package com.epam.drill.admin.writer.rawdata.config

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.Statement
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.ResultSet
import javax.sql.DataSource

object RawDataWriterDatabaseConfig {
    private var database: Database? = null
    private var dispatcher: CoroutineDispatcher = Dispatchers.IO
    private var dataSource: DataSource? = null

    fun init(dataSource: DataSource) {
        this.dataSource = dataSource
        this.database = Database.connect(dataSource)
        Flyway.configure()
            .dataSource(dataSource)
            .schemas("raw_data")
            .baselineOnMigrate(true)
            .locations("classpath:raw_data/db/migration")
            .load()
            .migrate()
    }

    suspend fun <T> transaction(block: suspend Transaction.() -> T): T =
        newSuspendedTransaction(dispatcher, database) { block() }
}