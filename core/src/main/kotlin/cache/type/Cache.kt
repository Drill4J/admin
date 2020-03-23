package com.epam.drill.admin.cache.type

interface Cache<K, V> {
    operator fun get(key: K): V?

    operator fun set(key: K, value: V): V?

    fun remove(key: K): V?
}
