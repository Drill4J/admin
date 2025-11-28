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
package com.epam.drill.admin.etl.impl

import kotlinx.coroutines.Dispatchers
import mu.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.Statement
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

abstract class SqlDataLoader<T>(
    override val name: String,
    override val batchSize: Int,
    open val sqlUpsert: String,
    open val sqlDelete: String,
    open val database: Database
) : BatchDataLoader<T>(name, batchSize) {
    private val logger = KotlinLogging.logger {}

    interface PreparedSql<T> {
        fun getSql(): String
        fun getArgs(row: T): List<Any?>
    }
    abstract fun prepareSql(sql: String): PreparedSql<T>

    override suspend fun loadBatch(
        batch: List<T>,
        batchNo: Int
    ): BatchResult {
        val preparedSql = prepareSql(sqlUpsert)
        val duration = try {
            newSuspendedTransaction(db = database) {
                exec(object : Statement<Unit>(StatementType.INSERT, emptyList()) {
                    override fun PreparedStatementApi.executeInternal(transaction: Transaction) {
                        batch.forEach { row ->
                            val columns = preparedSql.getArgs(row)
                            for (index in columns.indices) {
                                val value = columns[index]
                                if (value != null) {
                                    set(index + 1, value)
                                } else
                                    setNull(index + 1, TextColumnType())
                            }
                            addBatch()
                        }
                        executeBatch()
                    }
                    override fun prepareSQL(transaction: Transaction, prepared: Boolean): String = preparedSql.getSql()
                    override fun arguments(): Iterable<Iterable<Pair<IColumnType<*>, Any?>>> = emptyList()
                })
                duration
            }
        } catch (e: Exception) {
            return BatchResult(
                success = false,
                rowsLoaded = 0,
                errorMessage = "Error during loading data with loader $name: ${e.message ?: e.javaClass.simpleName}"
            )
        }
        return BatchResult(
            success = true,
            rowsLoaded = batch.size,
            duration = duration
        )
    }

    override suspend fun deleteAll() {
        logger.debug { "Loader [$name] deleting data" }
        val duration = try {
            newSuspendedTransaction(context = Dispatchers.IO, db = database) {
                exec(sqlDelete)
                duration
            }
        } catch (e: Exception) {
            logger.error(e) { "Error during deleting data with loader $name: ${e.message ?: e.javaClass.simpleName}" }
            throw e
        }
        logger.debug { "Loader [$name] deleted data in ${duration}ms" }
    }
}