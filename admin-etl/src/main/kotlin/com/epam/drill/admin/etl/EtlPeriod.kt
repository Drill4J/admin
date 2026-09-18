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
package com.epam.drill.admin.etl

import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZoneOffset.UTC
import java.time.temporal.ChronoUnit

/**
 * A bounded or unbounded day range (time-of-day is ignored) used to scope an ETL rerun.
 */
data class EtlPeriod(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
) {
    val isBounded: Boolean get() = from != null || to != null

    /**
     * Extraction lower bound (exclusive): midnight of [from] so the whole [from] day is included
     */
    val sinceTimestamp: java.time.Instant?
        get() = from?.atStartOfDay(ZoneOffset.UTC)?.toInstant()

    /**
     * Extraction upper bound (inclusive).
     */
    val untilTimestamp: java.time.Instant?
        get() = to?.plusDays(1)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.minusMillis(1)

    /** Whether the day ranges of two periods intersect. */
    fun overlaps(other: EtlPeriod): Boolean {
        val thisFrom = from ?: LocalDate.MIN
        val thisTo = to ?: LocalDate.MAX
        val otherFrom = other.from ?: LocalDate.MIN
        val otherTo = other.to ?: LocalDate.MAX
        return !thisFrom.isAfter(otherTo) && !otherFrom.isAfter(thisTo)
    }

    /** Persisted (NOT NULL) lower bound; [SENTINEL_FROM] represents an unbounded lower bound. */
    val storedFrom: LocalDate get() = from ?: SENTINEL_FROM

    /** Persisted (NOT NULL) upper bound; [SENTINEL_TO] represents an unbounded upper bound. */
    val storedTo: LocalDate get() = to ?: SENTINEL_TO

    val toExclusive: LocalDate? get() = to?.plusDays(1)

    companion object {
        val SENTINEL_FROM: LocalDate = LocalDate.of(2000, 1, 1)
        val SENTINEL_TO: LocalDate = LocalDate.of(2099, 12, 31)
        val UNBOUNDED = EtlPeriod()
        val FROM_TODAY: EtlPeriod
            get() = EtlPeriod(from = LocalDate.now(UTC))
        val BEFORE_TODAY: EtlPeriod
            get() = EtlPeriod(to = LocalDate.now(UTC).minusDays(1))
        val TODAY: EtlPeriod
            get() = EtlPeriod(to = LocalDate.now(UTC), from = LocalDate.now(UTC))

        /** Rebuilds an [EtlPeriod] from persisted sentinel-aware bounds. */
        fun fromStored(from: LocalDate, to: LocalDate): EtlPeriod {
            return EtlPeriod(from.takeUnless { it == SENTINEL_FROM }, to.takeUnless { it == SENTINEL_TO })
        }

    }

    override fun toString(): String {
        return "[" + (from?.toString() ?: "") + "," + (to?.toString() ?: "") + "]"
    }
}
