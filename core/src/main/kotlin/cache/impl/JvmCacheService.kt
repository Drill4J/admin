package com.epam.drill.admin.cache.impl

import com.epam.drill.admin.cache.*
import com.epam.drill.admin.cache.type.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*

class JvmCacheService : CacheService {
    private val _caches = atomic(persistentHashMapOf<Any, Cache<*, *>>())

    override fun <K, V> getOrCreate(
        id: Any,
        qualifier: Any
    ): Cache<K, V> = _caches.updateAndGet {
        it.takeIf { it[id]?.qualifier == qualifier } ?: it.put(
            id,
            PersistentMapCache<K, V>(qualifier)
        )
    }[id].let {
        @Suppress("UNCHECKED_CAST")
        it as Cache<K, V>
    }
}

private class PersistentMapCache<K, V>(
    override val qualifier: Any
) : Cache<K, V> {
    private val _values = atomic(persistentHashMapOf<K, V>())

    override fun get(key: K): V? = _values.value[key]

    override fun set(key: K, value: V): V? = _values.getAndUpdate { it.put(key, value) }[key]

    override fun remove(key: K): V? = _values.getAndUpdate { it.remove(key) }[key]
}
