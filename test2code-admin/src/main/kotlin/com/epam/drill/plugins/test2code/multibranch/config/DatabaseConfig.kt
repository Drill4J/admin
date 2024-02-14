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
package com.epam.drill.plugins.test2code.multibranch.rawdata.config

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import javax.sql.DataSource

object DatabaseConfig {

    private var database: Database? = null
    private var dispatcher: CoroutineDispatcher = Dispatchers.IO
    private var dataSource: DataSource? = null

    fun getDataSource() = this.dataSource

    fun init(dataSource: DataSource) {
        this.dataSource = dataSource
        database = Database.connect(dataSource)
        Flyway.configure()
            .dataSource(dataSource)
            .schemas("auth")
//            .schemas("test2code")
            .baselineOnMigrate(true)
            .locations("classpath:auth/db/migration")
//            .locations("classpath:test2code/db/migration")
            .load()
            .migrate()
    }

    suspend fun <T> transaction(block: suspend () -> T): T =
        newSuspendedTransaction(dispatcher, database) { block() }
}