package com.epam.drill.admin.cache.type

interface Cache<K, V> {
    val qualifier: Any

    operator fun get(key: K): V?

    operator fun set(key: K, value: V): V?

    fun remove(key: K): V?
}
