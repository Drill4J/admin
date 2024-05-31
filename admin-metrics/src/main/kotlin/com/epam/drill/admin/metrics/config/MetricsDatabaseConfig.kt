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
package com.epam.drill.admin.metrics.config

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.Statement
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.ResultSet
import javax.sql.DataSource

object MetricsDatabaseConfig {
    private var database: Database? = null
    private var dispatcher: CoroutineDispatcher = Dispatchers.IO
    private var dataSource: DataSource? = null

    fun init(dataSource: DataSource) {
        MetricsDatabaseConfig.dataSource = dataSource
        database = Database.connect(dataSource)
    }

    suspend fun <T> transaction(block: suspend Transaction.() -> T): T =
        newSuspendedTransaction(dispatcher, database) { block() }
}

// TODO allow to pass nullable params (vararg params: Any?)
fun Transaction.executeQuery(sqlQuery: String, vararg params: Any?): List<JsonObject> {
    val result = mutableListOf<JsonObject>()
    execSp(sqlQuery, *params) { resultSet ->
        val metaData = resultSet.metaData
        val columnCount = metaData.columnCount

        while (resultSet.next()) {
            val rowObject = buildJsonObject {
                for (i in 1..columnCount) {
                    val columnName = metaData.getColumnName(i)
                    val columnValue = resultSet.getObject(i)
                    val stringValue = columnValue?.toString()
                    put(columnName, Json.encodeToJsonElement(stringValue))
                }
            }
            result.add(rowObject)
        }
    }
    return result
}

// TODO allow to pass nullable params (vararg params: Any?)
fun Transaction.executeQueryReturnMap(sqlQuery: String, vararg params: Any?): List<Map<String,String>> {
    val result = mutableListOf<Map<String,String>>()
    execSp(sqlQuery, *params) { resultSet ->
        val metaData = resultSet.metaData
        val columnCount = metaData.columnCount

        while (resultSet.next()) {
            val rowObject = mutableMapOf<String,String>()

            for (i in 1..columnCount) {
                val columnName = metaData.getColumnName(i)
                val columnValue = resultSet.getObject(i)
                val stringValue = columnValue?.toString()
                rowObject[columnName] = stringValue ?: "null"
            }
            result.add(rowObject)
        }
    }
    return result
}

// TODO execSp - function name unclear
private fun <T : Any> Transaction.execSp(stmt: String, vararg params: Any?, transform: (ResultSet) -> T): T? {
    if (stmt.isEmpty()) return null

    return exec(object : Statement<T>(StatementType.SELECT, emptyList()) {
        override fun PreparedStatementApi.executeInternal(transaction: Transaction): T? {
            params.forEachIndexed { idx, value -> set(idx + 1, value ?: "NULL") }
            executeQuery()
            return resultSet?.use { transform(it) }
        }

        override fun prepareSQL(transaction: Transaction, prepared: Boolean): String = stmt
        override fun arguments(): Iterable<Iterable<Pair<IColumnType<*>, Any?>>> = emptyList()
    })
}
