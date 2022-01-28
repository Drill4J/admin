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
package com.epam.drill.admin.util

import kotlinx.collections.immutable.*

typealias BiSetMap<K, V> = Pair<SetMap<K, V>, SetMap<V, K>>

@Suppress("UNCHECKED_CAST")
fun <K, V> emptyBiSetMap() = emptyBiSetMap as BiSetMap<K, V>

fun <K, V> BiSetMap<K, V>.put(key: K, value: V): BiSetMap<K, V> = run {
    val newFirst = first.put(key, value)
    val newSecond = second.put(value, key)
    if (newFirst !== first || newSecond !== second) {
        newFirst to newSecond
    } else this
}

fun <K, V> BiSetMap<K, V>.remove(key: K, value: V): BiSetMap<K, V> = second[value].let { keySet ->
    if (key in keySet) {
        val newFirst = first.remove(setOf(key), value)
        val newSecond = second.remove(value)
        biSetMap(newFirst, newSecond)
    } else this
}

fun <K, V> BiSetMap<K, V>.remove(value: V): BiSetMap<K, V> = second[value].let { keySet ->
    if (keySet.any()) {
        biSetMap(first.remove(keySet, value), second.remove(value))
    } else this
}

class SetMap<K, V>(
    private val m: PersistentMap<K, PersistentSet<V>> = persistentMapOf(),
) {
    val size get() = m.size

    operator fun get(key: K) = m[key] ?: persistentSetOf()

    operator fun contains(key: K) = m.containsKey(key)

    fun any() = m.any()

    fun put(key: K, value: V): SetMap<K, V> = m[key]?.let {
        if (value !in it) {
            SetMap(m.put(key, it + value))
        } else this
    } ?: SetMap(
        m.put(
            key,
            persistentSetOf(value)
        )
    )

    fun remove(key: K): SetMap<K, V> = if (key in m) {
        SetMap(m - key)
    } else this

    fun remove(keys: Iterable<K>, value: V): SetMap<K, V> = if (keys.any()) {
        val mutated = m.mutate { mutMap ->
            for (key in keys) {
                mutMap[key]?.let { values ->
                    val newVal = values - value
                    if (newVal.any()) {
                        mutMap[key] = newVal
                    } else mutMap.remove(key)
                }
            }
        }
        if (mutated !== m) {
            SetMap(mutated)
        } else this
    } else this
}

private val emptySetMap = SetMap<Any, Any>()

private val emptyBiSetMap = BiSetMap(
    emptySetMap,
    emptySetMap
)

private fun <K, V> biSetMap(
    newFirst: SetMap<K, V>,
    newSecond: SetMap<V, K>,
): BiSetMap<K, V> {
    return if (newFirst.any() || newSecond.any()) {
        newFirst to newSecond
    } else emptyBiSetMap()
}
