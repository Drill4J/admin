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
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.Statement
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.postgresql.jdbc.PgArray
import org.postgresql.util.PGobject
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

fun Transaction.executeQueryReturnMap(sqlQuery: String, vararg params: Any?): List<Map<String, Any?>> {
    val result = mutableListOf<Map<String, Any?>>()
    executePreparedStatement(sqlQuery, *params) { resultSet ->
        val metaData = resultSet.metaData
        val columnCount = metaData.columnCount

        while (resultSet.next()) {
            val rowObject = mutableMapOf<String, Any?>()

            for (i in 1..columnCount) {
                val name = metaData.getColumnName(i)
                val value = when (val dbValue = resultSet.getObject(i)) {
                    is PgArray -> {
                        val array = dbValue.array
                        array?.let { it as Array<*> }?.toList() ?: emptyList<String>()
                    }

                    is PGobject -> {
                        val json = dbValue.value
                        if (json != null) {
                            val jsonObject = Json.parseToJsonElement(json).jsonObject
                            jsonObject.mapValues { (_, value) -> value.jsonPrimitiveOrElement() }
                        } else {
                            emptyMap()
                        }
                    }

                    is java.sql.Timestamp -> dbValue.toLocalDateTime()
                    else -> dbValue
                }

                rowObject[name] = if (resultSet.wasNull()) null else value
            }
            result.add(rowObject)
        }
    }
    return result
}

fun Transaction.executeUpdate(sql: String, vararg params: Any?) {
    exec(object : Statement<Unit>(StatementType.UPDATE, emptyList()) {
        override fun PreparedStatementApi.executeInternal(transaction: Transaction) {
            params.forEachIndexed { idx, value -> set(idx + 1, value ?: "NULL") }
            executeUpdate()
        }

        override fun prepareSQL(transaction: Transaction, prepared: Boolean): String = sql
        override fun arguments(): Iterable<Iterable<Pair<IColumnType<*>, Any?>>> = emptyList()
    })
}

fun Transaction.executeQueryReturnMap(buildSql: SqlBuilder.() -> Unit): List<Map<String, Any?>> {
    val builder = SqlBuilderImpl().apply { buildSql() }
    return executeQueryReturnMap(builder.sqlQuery.toString(), *builder.params.toTypedArray())
}

private fun <T : Any> Transaction.executePreparedStatement(
    stmt: String,
    vararg params: Any?,
    transform: (ResultSet) -> T
): T? {
    if (stmt.isEmpty()) return null

    return exec(object : Statement<T>(StatementType.SELECT, emptyList()) {
        override fun PreparedStatementApi.executeInternal(transaction: Transaction): T? {
            params.forEachIndexed { idx, value ->
                if (value != null) {
                    set(idx + 1, value)
                } else {
                    // WORKAROUND: TextColumnType is employed to trick expose to write null values
                    // Possible issues with: BinaryColumnType, BlobColumnType
                    // see setNull implementation for more details
                    setNull(idx + 1, TextColumnType())
                }
            }
            executeQuery()
            return resultSet?.use { transform(it) }
        }

        override fun prepareSQL(transaction: Transaction, prepared: Boolean): String = stmt
        override fun arguments(): Iterable<Iterable<Pair<IColumnType<*>, Any?>>> = emptyList()
    })
}

private fun JsonElement.jsonPrimitiveOrElement(): Any {
    return when (this) {
        is JsonPrimitive -> this.contentOrNull ?: this.booleanOrNull ?: this.longOrNull ?: this.doubleOrNull ?: ""
        is JsonObject -> this.mapValues { (_, value) -> value.jsonPrimitiveOrElement() }
        is JsonArray -> this.map { it.jsonPrimitiveOrElement() }
    }
}
