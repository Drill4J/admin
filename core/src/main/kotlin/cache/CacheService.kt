package com.epam.drill.admin.cache

import com.epam.drill.admin.cache.type.*
import kotlin.reflect.*

interface CacheService {
    fun <K, V> getOrCreateMapCache(cacheName: String): Cache<K, V>

    operator fun <K, V> getValue(thisRef: Any?, property: KProperty<*>): Cache<K, V> {
        return getOrCreateMapCache(property.name)
    }
}
