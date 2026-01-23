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
package com.epam.drill.admin.etl.flow

/**
 * A simple LRU (Least Recently Used) map implementation that evicts the oldest entry
 * when the maximum size is reached.
 */
class LruMap<K, V>(
    private val maxSize: Int,
    private val onEvict: (K, V) -> Unit
) {
    private val map = LinkedHashMap<K, V>(16, 0.75f, true)

    val size: Int
        get() = map.size

    fun compute(key: K, update: (V?) -> V) {
        map[key] = update(map[key])
        if (map.size >= maxSize) {
            evictOldest()
        }
    }

    fun evictOldest() {
        val it = map.entries.iterator()
        if (!it.hasNext()) return

        val entry = it.next()
        it.remove()
        onEvict(entry.key, entry.value)
    }

    fun evictAll() {
        val it = map.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            it.remove()
            onEvict(entry.key, entry.value)
        }
    }
}