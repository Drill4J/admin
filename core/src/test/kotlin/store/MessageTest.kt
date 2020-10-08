package com.epam.drill.admin.store

import kotlinx.serialization.*
import kotlin.reflect.jvm.*
import kotlin.test.*

class MessageTest {

    @Serializable
    data class Data(val s: String)

    @Test
    fun `toStoredMessage - object`() {
        val input = Data("data")
        input.toStoredMessage().apply {
            assertEquals(MessageKind.OBJECT, kind)
            assertEquals(Data::class.jvmName, className)
            assertNotEquals(0, data.size)
        }
    }

    @Test
    fun `toMessage - object`() {
        val input = Data("data")
        val storedMessage = input.toStoredMessage()
        assertEquals(input, storedMessage.toMessage())
    }

    @Test
    fun `toStoredMessage - empty list`() {
        val emptyList = emptyList<Any>()
        emptyList.toStoredMessage().apply {
            assertEquals(MessageKind.LIST, kind)
            assertEquals("", className)
            assertEquals(0, data.size)
        }
    }

    @Test
    fun `toMessage - empty list`() {
        val storedMessage = emptyList<Any>().toStoredMessage()
        assertEquals(emptyList<Any>(), storedMessage.toMessage())
    }

    @Test
    fun `toStoredMessage - empty set`() {
        val emptyList = emptySet<Any>()
        emptyList.toStoredMessage().apply {
            assertEquals(MessageKind.SET, kind)
            assertEquals("", className)
            assertEquals(0, data.size)
        }
    }

    @Test
    fun `toMessage - empty set`() {
        val storedMessage = emptySet<Any>().toStoredMessage()
        assertEquals(emptySet<Any>(), storedMessage.toMessage())
    }

    @Test
    fun `toStoredMessage toMessage - string list`() {
        val list = listOf("1", "2")
        list.toStoredMessage().apply {
            assertEquals(MessageKind.LIST, kind)
            assertEquals(list, toMessage())
        }
    }

    @Test
    fun `toStoredMessage toMessage - object set`() {
        val set = setOf(Data("1"), Data("2"))
        set.toStoredMessage().apply {
            assertEquals(MessageKind.SET, kind)
            assertEquals(set, toMessage())
        }
    }
}
