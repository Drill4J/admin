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

import com.epam.drill.admin.etl.BatchResult
import mu.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant

abstract class SqlDataLoader<T>(
    override val name: String,
    override val batchSize: Int,
    open val sqlUpsert: String,
    open val sqlDelete: String,
    open val database: Database
) : BatchDataLoader<T>(name, batchSize) {
    private val logger = KotlinLogging.logger {}

    abstract fun prepareSql(sql: String, args: T): String

    override suspend fun loadBatch(
        batch: List<T>,
        batchNo: Int
    ): BatchResult {
        val stmts = mutableListOf<String>()
        batch.forEach { data ->
            val preparedSql = prepareSql(sqlUpsert, data)
            stmts += preparedSql
        }
        val duration = try {
            newSuspendedTransaction(db = database) {
                execInBatch(stmts)
                duration
            }
        } catch (e: Exception) {
            return BatchResult(
                success = false,
                errorMessage = "Error during loading data with loader $name: ${e.message ?: e.javaClass.simpleName}"
            )
        }
        return BatchResult(
            success = true,
            duration = duration
        )
    }

    override suspend fun deleteAll() {
        logger.info { "Loader [$name] deleting data" }
        val duration = try {
            newSuspendedTransaction(db = database) {
                exec(sqlDelete)
                duration
            }
        } catch (e: Exception) {
            logger.error(e) { "Error during deleting data with loader $name: ${e.message ?: e.javaClass.simpleName}" }
            throw e
        }
        logger.info { "Loader [$name] deleted data in ${duration}ms" }
    }
}