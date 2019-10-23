package com.epam.drill.cache.impl

import com.epam.drill.cache.*
import com.epam.drill.cache.type.*

class JvmCacheService : CacheService {
    private val cacheMapper = mutableMapOf<String, Cache<*, *>>()

    override fun <K, V> getOrCreateMapCache(cacheName: String): Cache<K, V> {
        @Suppress("UNCHECKED_CAST")
        return cacheMapper[cacheName] as Cache<K, V>? ?: run {
            val map = HashMapCache<K, V>()
            cacheMapper[cacheName] = map
            map
        }
    }
}

class HashMapCache<K, V> : Cache<K, V> {
    val cache = mutableMapOf<K, V>()

    override fun get(key: K) = cache[key]

    override fun set(key: K, value: V) {
        cache[key] = value
    }

    override fun remove(key: K) {
        cache.remove(key)
    }
}
