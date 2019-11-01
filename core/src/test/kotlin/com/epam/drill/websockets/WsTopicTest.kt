package com.epam.drill.websockets

import com.epam.drill.common.*
import com.epam.drill.endpoints.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import org.junit.Test
import kotlin.test.*

internal class WsTopicKtTest {
    @Test
    fun `universal serialization proceeds correctly`() {
        val empty = serialize(null)
        assertEquals("", empty)
        val string = "someText: \"asdf\""
        val serializedString = serialize(string)
        assertEquals(string, serializedString)
        val complexStructure1 = mapOf("key" to WsSendMessage(WsMessageType.SUBSCRIBE, "asd", "vbn"))
        val serializedStructure1 = serialize(complexStructure1)
        val result1 = "{\"key\":{\"type\":\"SUBSCRIBE\",\"destination\":\"asd\",\"message\":\"vbn\"}}"
        assertEquals(result1, serializedStructure1)
        val complexStructure2 = arrayListOf(PluginConfig("1", "2"))
        val serializedStructure2 = serialize(complexStructure2)
        val result2 = "[{\"id\":\"1\",\"data\":\"2\"}]"
        assertEquals(result2, serializedStructure2)
        assertEquals("[]", serialize(emptyArray<String>()))
    }

}