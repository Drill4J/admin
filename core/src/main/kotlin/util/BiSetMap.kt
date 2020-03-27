package com.epam.drill.admin.util

import kotlinx.collections.immutable.*

typealias BiSetMap<K, V> = Pair<SetMap<K, V>, SetMap<V, K>>

@Suppress("UNCHECKED_CAST")
fun <K, V> emptyBiSetMap() = emptyBiSetMap as BiSetMap<K, V>

fun <K, V> BiSetMap<K, V>.put(key: K, value: V): BiSetMap<K, V> = run {
    val newFirst = first.put(key, value)
    val newSecond = second.put(value, key)
    if (newFirst !== first || newSecond !== second) {
        newFirst to newSecond
    } else this
}

fun <K, V> BiSetMap<K, V>.remove(key: K, value: V): BiSetMap<K, V> = second[value].let { keySet ->
    if (key in keySet) {
        val newFirst = first.remove(setOf(key), value)
        val newSecond = second.remove(value)
        biSetMap(newFirst, newSecond)
    } else this
}

fun <K, V> BiSetMap<K, V>.remove(value: V): BiSetMap<K, V> = second[value].let { keySet ->
    if (keySet.any()) {
        biSetMap(first.remove(keySet, value), second.remove(value))
    } else this
}

class SetMap<K, V>(
    private val m: PersistentMap<K, PersistentSet<V>> = persistentMapOf()
) {
    operator fun get(key: K) = m[key] ?: persistentSetOf()

    fun any() = m.any()

    fun put(key: K, value: V): SetMap<K, V> = m[key]?.let {
        if (value !in it) {
            SetMap(m.put(key, it + value))
        } else this
    } ?: SetMap(
        m.put(
            key,
            persistentSetOf(value)
        )
    )

    fun remove(key: K): SetMap<K, V> = if (key in m) {
        SetMap(m - key)
    } else this

    fun remove(keys: Iterable<K>, value: V): SetMap<K, V> = if (keys.any()) {
        val mutated = m.mutate { mutMap ->
            for (key in keys) {
                mutMap[key]?.let { values ->
                    val newVal = values - value
                    if (newVal.any()) {
                        mutMap[key] = newVal
                    } else mutMap.remove(key)
                }
            }
        }
        if (mutated !== m) {
            SetMap(mutated)
        } else this
    } else this
}

private val emptySetMap = SetMap<Any, Any>()

private val emptyBiSetMap = BiSetMap(
    emptySetMap,
    emptySetMap
)

private fun <K, V> biSetMap(
    newFirst: SetMap<K, V>,
    newSecond: SetMap<V, K>
): BiSetMap<K, V> {
    return if (newFirst.any() || newSecond.any()) {
        newFirst to newSecond
    } else emptyBiSetMap()
}
