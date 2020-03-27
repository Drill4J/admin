package com.epam.drill.admin.util

import kotlin.test.*

class BiSetMapTest {
    @Test
    fun `put key and value`() {
        val empty = emptyBiSetMap<String, Any>()
        val key = "key"
        val value = Any()
        val withData = empty.put(key, value)
        assertEquals(setOf(value), withData.first[key])
        assertEquals(setOf(key), withData.second[value])
    }

    @Test
    fun `remove value`() {
        val empty = emptyBiSetMap<String, Any>()
        val key = "key"
        val value = Any()
        val bsmap = empty.put(key, value)
        assertSame(empty, bsmap.remove(value))
        assertSame(bsmap, bsmap.remove(Any()))

    }

    @Test
    fun `remove key-value`() {
        val empty = emptyBiSetMap<String, Any>()
        val key = "key"
        val value = Any()
        val bsmap = empty.put(key, value)
        assertSame(empty, bsmap.remove(key, value))
        assertSame(bsmap, bsmap.remove(key, Any()))
        assertSame(bsmap, bsmap.remove("nokey", value))
    }
}
