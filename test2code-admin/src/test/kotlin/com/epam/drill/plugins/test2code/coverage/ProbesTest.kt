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
package com.epam.drill.plugins.test2code.coverage

import com.epam.drill.plugins.test2code.common.api.*
import kotlinx.collections.immutable.*
import kotlin.test.*

class ProbesTest {
    @Test
    fun `intersect - edge cases`() {
        val data = ExecClassData(
            id = 1L,
            className = "foo/Bar",
            probes = probesOf(true, true, false)
        )
        val map = persistentMapOf(data.id() to data)
        val emptyMap = persistentHashMapOf<Long, ExecClassData>()
        assertEquals(map, map.intersect(map.values.asSequence()))
        assertEquals(emptyMap, map.intersect(emptySequence()))
        assertEquals(emptyMap, emptyMap.intersect(map.values.asSequence()))
    }

    @Test
    fun `intersect - simple case`() {
        val data = ExecClassData(
            id = 1L,
            className = "foo/Bar",
            probes = probesOf(true, true, false)
        )
        val data2 = data.copy(
            id = 2L,
            className = "bar/Baz",
            probes = probesOf(true, false, false)

        )
        val map = listOf(
            data,
            data2
        ).associateBy { it.id() }.toPersistentMap()
        val expected = data.run {
            persistentMapOf(id() to copy(probes = probesOf(false, true, false)))
        }
        val intersection = map.intersect(
            sequenceOf(
                data.copy(probes = probesOf(false, true, true)),
                data.copy(probes = probesOf(false, false, true)),
                data2.copy(probes = probesOf(false, true, true))
            )
        )
        assertEquals(expected, intersection)
    }

    //todo move it to BitSet tests.
    @Test
    fun `merge - simple case`() {
        (booleanArrayOf(true, true, false) to booleanArrayOf(false, true, true)).let { (fbs, sbs) ->
            assertEquals(
                probesOf(*fbs).merge(probesOf(*sbs)).toList(),
                fbs.toList().merge(sbs.toList())
            )
        }
        (booleanArrayOf(true, true, true) to booleanArrayOf(true, true, true)).let { (fbs, sbs) ->
            assertEquals(
                probesOf(*fbs).merge(probesOf(*sbs)).toList(),
                fbs.toList().merge(sbs.toList())
            )
        }

        (booleanArrayOf(false, false, false) to booleanArrayOf(false, false, false)).let { (fbs, sbs) ->
            assertEquals(
                probesOf(*fbs).merge(probesOf(*sbs)).toList(),
                fbs.toList().merge(sbs.toList())
            )
        }
    }

    @Test
    fun `merge - edge case`() {
        val fbs = booleanArrayOf(true, true, false)
        val sbs = booleanArrayOf(true, false, false, false, true)
        assertEquals(
            probesOf(*fbs).merge(probesOf(*sbs)).toList(),
            fbs.toList().merge(sbs.toList())
        )
    }
}
