package com.epam.drill.admin.auth.config

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import javax.sql.DataSource

object DatabaseConfig {

    private lateinit var database: Database
    private lateinit var dbDispatcher: CoroutineDispatcher

    fun init(dataSource: DataSource) {
        database = Database.connect(dataSource)
        dbDispatcher = Dispatchers.IO
        Flyway.configure()
            .dataSource(dataSource)
            .schemas("auth")
            .baselineOnMigrate(true)
            .locations("classpath:auth/db/migration")
            .load()
            .migrate()
    }

    suspend fun <T> transaction(block: suspend () -> T): T =
        newSuspendedTransaction(dbDispatcher, database) { block() }
}