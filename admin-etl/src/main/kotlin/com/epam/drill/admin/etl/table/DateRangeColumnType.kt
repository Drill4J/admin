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
package com.epam.drill.admin.etl.table

import org.jetbrains.exposed.sql.IColumnType
import org.postgresql.util.PGobject
import java.time.LocalDate

/** An inclusive `[from, to]` day range, mapped to/from a PostgreSQL `daterange`. */
data class DateRangeValue(val from: LocalDate, val to: LocalDate)

/**
 * Maps a Kotlin [DateRangeValue] (inclusive bounds) to/from a PostgreSQL `daterange` column.
 *
 * PostgreSQL normalizes any `daterange` literal to the canonical `[from, to)` form, so a value
 * written as `[from, to]` is read back as `[from, to+1)`; this type compensates by subtracting
 * one day from the upper bound on read so [DateRangeValue.to] stays inclusive.
 */
class DateRangeColumnType(override var nullable: Boolean = false) : IColumnType<DateRangeValue> {
    override fun sqlType(): String = "daterange"

    override fun valueFromDB(value: Any): DateRangeValue = when (value) {
        is DateRangeValue -> value
        is PGobject -> parse(value.value ?: error("Empty daterange value"))
        is String -> parse(value)
        else -> throw IllegalStateException("Unsupported value type for daterange: ${value::class}")
    }

    override fun notNullValueToDB(value: DateRangeValue): Any = toPgObject(value)

    companion object {
        /** Formats [value] as the canonical PostgreSQL `daterange` literal (`[from, to+1)`). */
        fun toLiteral(value: DateRangeValue): String = "[${value.from},${value.to.plusDays(1)})"

        fun toPgObject(value: DateRangeValue): PGobject = PGobject().apply {
            type = "daterange"
            this.value = toLiteral(value)
        }

        /** Parses a PostgreSQL `daterange` textual representation into an inclusive [DateRangeValue]. */
        fun parse(raw: String): DateRangeValue {
            val trimmed = raw.trim()
            val lowerInclusive = trimmed.startsWith("[")
            val upperInclusive = trimmed.endsWith("]")
            val body = trimmed.removePrefix("[").removePrefix("(").removeSuffix(")").removeSuffix("]")
            val (fromRaw, toRaw) = body.split(",", limit = 2)
            val from = LocalDate.parse(fromRaw.trim())
            var to = LocalDate.parse(toRaw.trim())
            if (!upperInclusive) to = to.minusDays(1)
            val adjustedFrom = if (lowerInclusive) from else from.plusDays(1)
            return DateRangeValue(adjustedFrom, to)
        }
    }
}
