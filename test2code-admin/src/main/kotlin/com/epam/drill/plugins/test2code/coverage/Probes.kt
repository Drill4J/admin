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

import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import kotlinx.collections.immutable.*

/**
 * Merge probes in same classes
 */
internal fun Sequence<ExecClassData>.merge(): PersistentMap<Long, ExecClassData> = run {
    persistentMapOf<Long, ExecClassData>().merge(this)
}

/**
 * Merge probes in same classes
 */
internal fun PersistentMap<Long, ExecClassData>.merge(
    probes: Sequence<ExecClassData>,
): PersistentMap<Long, ExecClassData> = if (probes.any()) {
    mutate { map ->
        probes.forEach { data ->
            data.id().let { map[it] = map[it]?.merge(data) ?: data }
        }
    }
} else this

/**
 * Calculate the intersection of probes by class
 * @param other probes of the class with which the intersection is calculated
 * @return a map of only intersecting probes
 */
internal fun PersistentMap<Long, ExecClassData>.intersect(
    other: Sequence<ExecClassData>,
): PersistentMap<Long, ExecClassData> = if (any() && other.any()) {
    other.merge().let { merged ->
        merged.mutate { map ->
            for ((id, datum) in merged) {
                this[id]?.probes?.run { intersect(datum.probes).takeIf { true in it } }?.let {
                    map[id] = datum.copy(probes = it)
                } ?: map.remove(id)
            }
        }
    }
} else persistentMapOf()

/**
 * Merge probes of two classes
 */
internal fun ExecClassData.merge(other: ExecClassData): ExecClassData = copy(
    probes = probes.merge(other.probes)
)

internal fun List<Boolean>.intersect(other: List<Boolean>): List<Boolean> = mapIndexed { i, b ->
    if (i < other.size) {
        b && other[i]
    } else false
}

fun List<Boolean>.merge(other: List<Boolean>): List<Boolean> = mapIndexed { i, b ->
    if (i < other.size) {
        b || other[i]
    } else b
}


operator fun Probes.contains(value: Boolean): Boolean {
    if (size == 0) return false
    return copy().let {
        it.set(size, false)
        if (value) {
            !it.isEmpty
        } else {
            it.isEmpty  //fixme for false value
        }
    }
}

fun Probes.copy(): Probes {
    return clone() as Probes
}

val Probes.size
    get() = maxOf(0, this.length() - 1) //bitcode magic

fun Probes.merge(set: Probes): Probes {
    return copy().apply {
        (0 until length()).forEach {
            set(it, get(it) or set.get(it))
        }
    }
}

inline fun Probes.any(predicate: (Boolean) -> Boolean): Boolean {
    return !isEmpty //fixme for false value
}

fun Probes.intersect(set: Probes): Probes {
    return copy().apply { and(set) }
}

fun Probes.covered(): Int {
    return maxOf(0, cardinality() - 1)
}

internal fun Probes.toCount() = Count(maxOf(0, cardinality() - 1), size)
