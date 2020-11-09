package com.epam.drill.admin.cache

import com.epam.drill.admin.cache.type.*
import kotlin.reflect.*

interface CacheService {
    fun <K, V> getOrCreate(id: Any, qualifier: Any = ""): Cache<K, V>
}

operator fun <K, V> CacheService.getValue(thisRef: Any?, property: KProperty<*>): Cache<K, V> {
    return getOrCreate(property.name)
}
