package com.epam.drill.admin.etl.impl

import com.epam.drill.admin.etl.DataLoader
import org.jetbrains.exposed.sql.Database
import java.time.Instant
import java.util.Date

class UntypedSqlDataLoader(
    name: String,
    sql: String,
    database: Database,
    private val lastExtractedAtColumnName: String,
) : SqlDataLoader<Map<String, Any?>>(name, sql, database), DataLoader<Map<String, Any?>> {
    override fun prepareSql(
        sql: String,
        args: Map<String, Any?>
    ): String {
        var preparedSql = sql
        args.forEach {
            val value = when (val v = it.value) {
                null -> "NULL"
                is String -> "'${v.replace("'", "''")}'"
                is Instant -> "'$v'"
                is Date -> "'$v'"
                else -> "'$v'"
            }
            preparedSql = preparedSql.replace(":${it.key}", value)
        }
        return preparedSql
    }

    override fun getLastExtractedTimestamp(args: Map<String, Any?>): Instant? {
        val timestamp = args[lastExtractedAtColumnName]
        return when (timestamp) {
            is Instant -> timestamp
            is Date -> timestamp.toInstant()
            is String -> Instant.parse(timestamp)
            else -> null
        }
    }
}