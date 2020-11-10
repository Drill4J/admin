package com.epam.drill.admin.cache.impl

import com.epam.drill.admin.cache.*
import com.epam.drill.admin.cache.type.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*

class JvmCacheService : CacheService {

    private val _caches = atomic(persistentHashMapOf<Any, Cache<*, *>>())

    override fun <K, V> getOrCreate(
        id: Any,
        qualifier: Any,
        replace: Boolean
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
    override val qualifier: Any
) : Cache<K, V> {
    private val _values = atomic(persistentHashMapOf<K, V>())

    override fun get(key: K): V? = _values.value[key]

    override fun set(key: K, value: V): V? = _values.getAndUpdate { it.put(key, value) }[key]

    override fun remove(key: K): V? = _values.getAndUpdate { it.remove(key) }[key]
}

internal object NullCache : Cache<Any, Any> {
    override val qualifier = ""

    override fun get(key: Any): Any? = null

    override fun set(key: Any, value: Any): Any? = null

    override fun remove(key: Any): Any? = null
}
