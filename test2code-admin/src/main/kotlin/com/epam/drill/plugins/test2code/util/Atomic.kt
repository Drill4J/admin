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
package com.epam.drill.plugins.test2code.util

import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*

class AtomicCache<K, V> : (K, (V?) -> V?) -> V? {

    private val _map = atomic(persistentHashMapOf<K, V>())

    val map get() = _map.value

    override fun invoke(key: K, mutator: (V?) -> V?) = _map.updateAndGet {
        val oldVal = it[key]
        when (val newVal = mutator(oldVal)) {
            oldVal -> it
            null -> it.remove(key)
            else -> it.put(key, newVal)
        }
    }[key]


    operator fun get(key: K): V? = map[key]

    operator fun contains(key: K): Boolean = key in map

    operator fun set(key: K, value: V): V? = this(key) { value }

    fun putAll(m: Map<out K, V>) = _map.getAndUpdate { it.putAll(m) }

    fun remove(key: K) = _map.getAndUpdate { it.remove(key) }[key]

    fun clear() = _map.getAndUpdate { it.clear() }

    override fun toString(): String = map.toString()
}

val <K, V> AtomicCache<K, V>.keys get() = map.keys

val <K, V> AtomicCache<K, V>.values get() = map.values

fun <K, V> AtomicCache<K, V>.getOrPut(key: K, producer: () -> V): V = this(key) { it ?: producer() }!!

fun <K, V> AtomicCache<K, V>.count() = map.count()

fun <K, V> AtomicCache<K, V>.isEmpty() = map.isEmpty()

fun <K, V> AtomicCache<K, V>.isNotEmpty() = !isEmpty()
