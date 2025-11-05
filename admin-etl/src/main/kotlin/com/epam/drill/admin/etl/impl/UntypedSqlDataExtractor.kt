package com.epam.drill.admin.etl.impl

import org.jetbrains.exposed.sql.Database
import org.postgresql.util.PGobject
import java.sql.ResultSet
import java.sql.ResultSetMetaData

class UntypedSqlDataExtractor(
    name: String,
    sqlQuery: String,
    database: Database
) : SqlDataExtractor<Map<String, Any?>>(name, sqlQuery, database) {
    override fun parseRow(rs: ResultSet, meta: ResultSetMetaData, columnCount: Int): Map<String, Any?> {
        val row = mutableMapOf<String, Any?>()
        for (i in 1..columnCount) {
            val columnName = meta.getColumnName(i)
            val value = when (meta.getColumnTypeName(i)) {
                "bit", "varbit" -> {
                    val bit = rs.getObject(i)
                    when (bit) {
                        is Boolean -> if (bit) "1" else "0"
                        is String -> bit
                        is PGobject -> bit.value ?: ""
                        else -> throw IllegalStateException("Unsupported BIT/VARBIT type: ${bit?.javaClass?.name}")
                    }
                }

                else -> rs.getObject(i)
            }
            row[columnName] = value
        }
        return row
    }
}