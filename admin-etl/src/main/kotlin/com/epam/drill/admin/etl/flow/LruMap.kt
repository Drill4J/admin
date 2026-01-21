package com.epam.drill.admin.etl.flow

/**
 * A simple LRU (Least Recently Used) map implementation that evicts the oldest entry
 * when the maximum size is reached.
 */
class LruMap<K, V>(
    private val maxSize: Int,
    private val onEvict: (K, V) -> Unit
) {
    private val map = LinkedHashMap<K, V>(16, 0.75f, true)

    val size: Int
        get() = map.size

    fun compute(key: K, update: (V?) -> V) {
        map[key] = update(map[key])
        if (map.size >= maxSize) {
            evictOldest()
        }
    }

    fun evictOldest() {
        val it = map.entries.iterator()
        if (!it.hasNext()) return

        val entry = it.next()
        it.remove()
        onEvict(entry.key, entry.value)
    }

    fun evictAll() {
        val it = map.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            it.remove()
            onEvict(entry.key, entry.value)
        }
    }
}