/**
 * Copyright 2020 EPAM Systems
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
package com.epam.drill.admin.cache.impl

import com.epam.drill.admin.cache.*
import com.epam.drill.admin.cache.type.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import mu.*

private val logger = KotlinLogging.logger {}

class JvmCacheService : CacheService {

    private val _caches = atomic(persistentHashMapOf<Any, Cache<*, *>>())

    override fun <K, V> getOrCreate(
        id: Any,
        qualifier: Any,
        replace: Boolean,
    ): Cache<K, V> = _caches.updateAndGet { map ->
        map[id]?.let { cache ->
            map.takeIf { !replace || cache.qualifier == qualifier }
        } ?: map.put(id, PersistentMapCache<K, V>(qualifier))
    }[id].let {
        it?.takeIf { it.qualifier == qualifier } ?: NullCache
    }.castUnchecked()
}

@Suppress("UNCHECKED_CAST")
internal fun <K, V> Cache<*, *>?.castUnchecked(): Cache<K, V> = this as Cache<K, V>

internal class PersistentMapCache<K, V>(
    override val qualifier: Any,
) : Cache<K, V> {
    private val _values = atomic(persistentHashMapOf<K, V>())

    override fun get(key: K): V? = _values.value[key]
        .also { logger.trace { "get key $key" } }

    override fun set(key: K, value: V): V? = _values.getAndUpdate { it.put(key, value) }[key]
        .also { logger.trace { "set key $key" } }

    override fun remove(key: K): V? = _values.getAndUpdate { it.remove(key) }[key]
        .also { logger.trace { "remove key $key" } }
}

internal object NullCache : Cache<Any, Any> {
    override val qualifier = ""

    override fun get(key: Any): Any? = null

    override fun set(key: Any, value: Any): Any? = null

    override fun remove(key: Any): Any? = null
}
