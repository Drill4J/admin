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

import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EtlPeriodTest {

    @Test
    fun `unbounded period has no bounds and maps to sentinels`() {
        val period = EtlPeriod.UNBOUNDED
        assertFalse(period.isBounded)
        assertNull(period.sinceDay)
        assertNull(period.untilDay)
        assertNull(period.sinceTimestamp)
        assertNull(period.untilTimestamp)
        assertEquals(EtlPeriod.SENTINEL_FROM, period.storedFrom)
        assertEquals(EtlPeriod.SENTINEL_TO, period.storedTo)
    }

    @Test
    fun `bounded period derives inclusive-day extraction window`() {
        val from = LocalDate.of(2024, 1, 10)
        val to = LocalDate.of(2024, 1, 12)
        val period = EtlPeriod(from, to)

        assertTrue(period.isBounded)
        // since is just before midnight of `from` (extractor filters created_at > since)
        assertEquals(
            from.atStartOfDay(ZoneOffset.UTC).toInstant().minus(1, ChronoUnit.NANOS),
            period.sinceTimestamp
        )
        // until is midnight of the day after `to` (extractor filters created_at <= until)
        assertEquals(
            to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
            period.untilTimestamp
        )
        assertEquals(from, period.storedFrom)
        assertEquals(to, period.storedTo)
    }

    @Test
    fun `fromStored round-trips sentinels back to nulls`() {
        val restored = EtlPeriod.fromStored(EtlPeriod.SENTINEL_FROM, EtlPeriod.SENTINEL_TO)
        assertEquals(EtlPeriod.UNBOUNDED, restored)

        val bounded = EtlPeriod(LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 31))
        assertEquals(bounded, EtlPeriod.fromStored(bounded.storedFrom, bounded.storedTo))
    }

    @Test
    fun `overlaps is symmetric and detects intersecting ranges`() {
        val a = EtlPeriod(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 10))
        val b = EtlPeriod(LocalDate.of(2024, 1, 10), LocalDate.of(2024, 1, 20)) // touches at day 10
        val c = EtlPeriod(LocalDate.of(2024, 1, 11), LocalDate.of(2024, 1, 20)) // disjoint from a

        assertTrue(a.overlaps(b))
        assertTrue(b.overlaps(a))
        assertFalse(a.overlaps(c))
        assertFalse(c.overlaps(a))
    }
}
