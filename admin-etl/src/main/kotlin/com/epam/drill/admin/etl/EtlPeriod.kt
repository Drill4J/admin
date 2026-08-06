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
import java.time.temporal.ChronoUnit

/**
 * A bounded or unbounded day range (time-of-day is ignored) used to scope an ETL rerun.
 *
 * - [from] `== null` && [to] `== null` → the unbounded/incremental lane (the live `run()` watermark).
 * - both set → the inclusive day range `[from, to]`.
 *
 * Bounded periods get their own metadata (watermark) and run-lock rows, so they run
 * independently of the incremental run and, when non-overlapping, in parallel.
 */
data class EtlPeriod(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
) {
    val isBounded: Boolean get() = from != null || to != null

    /** Inclusive lower bound on `created_at_day` (midnight of [from]), or `null` when unbounded. */
    val sinceDay: java.time.Instant? get() = from?.atStartOfDay(ZoneOffset.UTC)?.toInstant()

    /** Inclusive upper bound on `created_at_day` (midnight of [to]), or `null` when unbounded. */
    val untilDay: java.time.Instant? get() = to?.atStartOfDay(ZoneOffset.UTC)?.toInstant()

    /**
     * Extraction lower bound: just before midnight of [from] because extractors filter
     * with `created_at > :since_timestamp`. `null` when the lower bound is unbounded.
     */
    val sinceTimestamp: java.time.Instant?
        get() = from?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.minus(1, ChronoUnit.NANOS)

    /**
     * Extraction upper bound (exclusive): midnight of the day after [to] so the whole
     * [to] day is included (extractors filter with `created_at <= :until_timestamp`).
     * `null` when the upper bound is unbounded.
     */
    val untilTimestamp: java.time.Instant?
        get() = to?.plusDays(1)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()

    /** Whether the day ranges of two periods intersect (used for same-context serialization). */
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

    companion object {
        val SENTINEL_FROM: LocalDate = LocalDate.of(1, 1, 1)
        val SENTINEL_TO: LocalDate = LocalDate.of(9999, 12, 31)
        val UNBOUNDED = EtlPeriod()

        /** Rebuilds an [EtlPeriod] from persisted sentinel-aware bounds. */
        fun fromStored(from: LocalDate, to: LocalDate): EtlPeriod =
            EtlPeriod(from.takeUnless { it == SENTINEL_FROM }, to.takeUnless { it == SENTINEL_TO })
    }
}
