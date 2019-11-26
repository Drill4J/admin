package com.epam.drill.websockets

import com.epam.drill.common.*
import com.epam.drill.endpoints.*
import io.ktor.http.*
import org.junit.Test
import kotlin.reflect.*
import kotlin.test.*

typealias Pair = kotlin.Pair<Map.Entry<URLTopic, kotlin.Pair<KClass<*>, CallbackWrapper<Any, Any>>>, Parameters>

val Pair.url
    get() = first.key

val Pair.parameters
    get() = second

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

    private val testMutableMap = mutableMapOf(
        URLTopic("/{any}/{anywhere}/{anyhow}") to (WsTopicKtTest::class as KClass<*> to CallbackWrapper {}),
        URLTopic("/{any}/{anywhere}/xxx") to (WsTopicKtTest::class as KClass<*> to CallbackWrapper {}),
        URLTopic("/{any}/xxx/{anywhere}") to (WsTopicKtTest::class as KClass<*> to CallbackWrapper {}),
        URLTopic("/{any}/xxx/yyy") to (WsTopicKtTest::class as KClass<*> to CallbackWrapper {}),
        URLTopic("/xxx/{any}/{anywhere}") to (WsTopicKtTest::class as KClass<*> to CallbackWrapper {}),
        URLTopic("/xxx/{any}/yyy") to (WsTopicKtTest::class as KClass<*> to CallbackWrapper {}),
        URLTopic("/xxx/yyy/{any}") to (WsTopicKtTest::class as KClass<*> to CallbackWrapper<Any, Any> {})
    )

    private fun par(st: String) = testMutableMap.suitableRoutWithParameters(st)


    @Test
    fun `check method find Map And Parameters By Url`() {
        with(par("/xxx/random2/yyy")) {
            assertEquals(URLTopic("/xxx/{any}/yyy"), this.url)
            assertEquals(this.parameters["any"], "random2")
        }

        with(par("/random1/xxx/yyy")) {
            assertEquals(URLTopic("/{any}/xxx/yyy"), this.url)
            assertEquals("random1", this.parameters["any"])
        }

        with(par("/xxx/yyy/random3")) {
            assertEquals(URLTopic("/xxx/yyy/{any}"), this.url)
            assertEquals("random3", this.parameters["any"])
        }

        with(par("/xxx/random2/random3")) {
            assertEquals(URLTopic("/xxx/{any}/{anywhere}"), this.url)
            assertEquals("random2", this.parameters["any"])
            assertEquals("random3", this.parameters["anywhere"])
        }

        with(par("/random1/xxx/random3")) {
            assertEquals(URLTopic("/{any}/xxx/{anywhere}"), this.url)
            assertEquals("random1", this.parameters["any"])
            assertEquals("random3", this.parameters["anywhere"])
        }

        with(par("/random1/random2/xxx")) {
            assertEquals(URLTopic("/{any}/{anywhere}/xxx"), this.url)
            assertEquals("random2", this.parameters["anywhere"])
            assertEquals("random1", this.parameters["any"])
        }

        with(par("/random1/random2/random3")) {
            assertEquals(URLTopic("/{any}/{anywhere}/{anyhow}"), this.url)
            assertEquals("random1", this.parameters["any"])
            assertEquals("random2", this.parameters["anywhere"])
            assertEquals("random3", this.parameters["anyhow"])
        }

    }


}