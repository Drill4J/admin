package com.epam.drill.admin.cache.impl

import com.epam.drill.admin.cache.*
import com.epam.drill.admin.cache.type.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*

class JvmCacheService : CacheService {
    private val cacheMapper = atomic(persistentHashMapOf<String, Cache<*, *>>())

    override fun <K, V> getOrCreateMapCache(cacheName: String): Cache<K, V> {
        @Suppress("UNCHECKED_CAST")
        return cacheMapper.value[cacheName] as Cache<K, V>? ?: run {
            cacheMapper.updateAndGet {
                cacheMapper.value.put(cacheName, AtomicCache<K, V>())
            }[cacheName] as Cache<K, V>
        }
    }
}

class AtomicCache<K, V> : Cache<K, V>, (K, (V?) -> V?) -> V? {
    private val cache = atomic(persistentHashMapOf<K, V>())

    override fun get(key: K) = cache.value[key]

    override fun invoke(key: K, mutator: (V?) -> V?): V? = cache.updateAndGet {
        val oldVal = it[key]
        when (val newVal = mutator(oldVal)) {
            oldVal -> it
            null -> it.remove(key)
            else -> it.put(key, newVal)
        }
    }[key]

    override fun set(key: K, value: V): V? = this(key) { value }

    override fun remove(key: K): V? = cache.getAndUpdate { it.remove(key) }[key]
}
