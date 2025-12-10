package com.epam.drill.admin.etl.impl

class UntypedPreparedSql(val preparedSql: String, val indexes: List<String>) : PreparedSql<Map<String, Any?>> {
    override fun getSql() = preparedSql
    override fun getArgs(row: Map<String, Any?>): List<Any?> {
        return indexes.map { row[it] }
    }

    companion object {
        fun prepareSql(sql: String): PreparedSql<Map<String, Any?>> {
            val regex = Regex("""(?<!:):([a-zA-Z_][a-zA-Z0-9_]*)(?![:=])""")

            val indexes = mutableListOf<String>()

            val prepared = regex.replace(sql) { match ->
                val name = match.groupValues[1]
                indexes += name
                "?"
            }

            return UntypedPreparedSql(prepared, indexes)
        }
    }
}