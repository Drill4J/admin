package com.epam.drill.admin.writer.rawdata.config

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import javax.sql.DataSource

object RawDataWriterDatabaseConfig {
    private var database: Database? = null
    private var dispatcher: CoroutineDispatcher = Dispatchers.IO
    private var dataSource: DataSource? = null

    fun getDataSource(): DataSource? = dataSource

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

    suspend fun <T> transaction(block: suspend () -> T): T =
        newSuspendedTransaction(dispatcher, database) { block() }
}