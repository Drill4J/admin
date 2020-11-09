package com.epam.drill.admin.endpoints

import com.epam.drill.admin.*
import com.epam.drill.admin.api.websocket.*
import com.epam.drill.admin.cache.*
import com.epam.drill.admin.cache.impl.*
import com.epam.drill.admin.common.*
import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.endpoints.plugin.*
import com.epam.drill.admin.endpoints.system.*
import com.epam.drill.admin.kodein.*
import com.epam.drill.admin.plugin.*
import com.epam.drill.admin.storage.*
import com.epam.drill.admin.store.*
import com.epam.drill.plugin.api.end.*
import com.epam.drill.testdata.*
import com.epam.kodux.*
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.cio.websocket.*
import io.ktor.locations.*
import io.ktor.serialization.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.kodein.di.*
import org.kodein.di.generic.*
import java.io.*
import java.util.*
import kotlin.test.*

class PluginWsTest {

    private val projectDir = File("build/tmp/test/${this::class.simpleName}-${UUID.randomUUID()}")


    @Serializable
    data class TestMessage(val message: String)

    private val pluginId = testPlugin.pluginId

    private val agentId = "testAgent"
    private val buildVersion = "1.0.0"

    private val storageDir = File("build/tmp/test/${this::class.simpleName}-${UUID.randomUUID()}")

    private lateinit var kodeinApplication: Kodein

    private val testApp: Application.() -> Unit = {
        install(Locations)
        install(WebSockets)

        install(ContentNegotiation) {
            json()
        }

        enableSwaggerSupport()
        kodeinApplication = kodeinApplication(AppBuilder {

            val baseLocation = projectDir.resolve(UUID.randomUUID().toString())

            withKModule { kodeinModule("pluginServices", pluginServices) }
            withKModule {
                kodeinModule("test") {
                    bind<StoreManager>() with eagerSingleton {
                        StoreManager(baseLocation.resolve("agent")).also {
                            app.onStop { it.close() }
                        }
                    }
                    bind<CommonStore>() with eagerSingleton {
                        CommonStore(baseLocation.resolve("common")).also {
                            app.closeOnStop(it)
                        }
                    }
                    bind<LoginEndpoint>() with eagerSingleton { LoginEndpoint(instance()) }
                    bind<PluginStores>() with eagerSingleton { PluginStores(storageDir).also { app.closeOnStop(it) } }
                    bind<DrillPluginWs>() with eagerSingleton { DrillPluginWs(kodein) }
                    bind<WsTopic>() with singleton {
                        WsTopic(
                            kodein
                        )
                    }
                    bind<CacheService>() with eagerSingleton { JvmCacheService() }
                    bind<AgentStorage>() with eagerSingleton { AgentStorage() }
                    bind<AgentManager>() with eagerSingleton { AgentManager(kodein) }
                }

            }
        })
    }

    @AfterTest
    fun removeStore() {
        storageDir.deleteRecursively()
    }

    @Test
    fun `should return empty message on subscribing without info`() {
        withTestApplication(testApp) {
            handleWebSocketConversation(socketUrl()) { incoming, outgoing ->
                val destination = "/pluginTopic1"
                outgoing.send(uiMessage(Subscribe(destination)))
                val receive = incoming.receive() as? Frame.Text ?: fail()
                val readText = receive.readText()
                val fromJson = json.parseJson(readText) as JsonObject
                assertEquals(destination, fromJson[WsSendMessage::destination.name]?.content)
                assertEquals(WsMessageType.MESSAGE.name, fromJson["type"]?.content)
                assertEquals("", fromJson[WsSendMessage::message.name]?.content)
            }
        }
    }


    @Test
    fun `should return empty message on subscribing with info`() {
        withTestApplication(testApp) {
            handleWebSocketConversation(socketUrl()) { incoming, outgoing ->
                val destination = "/pluginTopic2"
                outgoing.send(
                    uiMessage(
                        Subscribe(
                            destination,
                            Subscription.serializer() stringify AgentSubscription(agentId, buildVersion)
                        )

                    )
                )
                val receive = incoming.receive() as? Frame.Text ?: fail()
                val readText = receive.readText()
                val fromJson = json.parseJson(readText) as JsonObject
                assertEquals(destination, fromJson[WsSendMessage::destination.name]?.content)
                assertEquals(WsMessageType.MESSAGE.name, fromJson["type"]?.content)
                assertEquals("", fromJson[WsSendMessage::message.name]?.content)
            }
        }
    }

    @Serializable
    data class Data(val field1: String, val field2: Int, val notSortable: Noncomparable = Noncomparable(""))

    @Serializable
    class Noncomparable(@Suppress("unused") val s: String = "")

    @Test
    fun `should apply filters to list topics`() {
        withTestApplication(testApp) {
            val fieldName = Data::field1.name
            handleWebSocketConversation(socketUrl()) { incoming, outgoing ->

                val destination = "/ws/plugins/test-plugin"
                val message = listOf(
                    Data("x11", 16),
                    Data("x22", 14),
                    Data("x31", 10),
                    Data("x21", 12, Noncomparable("a"))
                )


                subscribe(outgoing, destination)
                assertEquals("", readMessage(incoming)?.content, "first subscription should be empty")


                sendListData(destination, message)
                assertEquals(message.size, (readMessage(incoming) as JsonArray).size)


                subscribe(outgoing, destination, setOf(FieldFilter(fieldName, "x11")))
                assertEquals(1, (readMessage(incoming) as JsonArray).size)

                unsubscribe(outgoing, destination)

                subscribe(
                    outgoing,
                    destination,
                    setOf(FieldFilter(fieldName, "x2", FieldOp.CONTAINS)),
                    setOf(FieldOrder(fieldName))
                )
                (readMessage(incoming) as JsonArray).let { array ->
                    assertEquals(2, array.size)
                    assertEquals(listOf("x21", "x22"), array.map { it.jsonObject[fieldName]?.content })
                }

                unsubscribe(outgoing, destination)
                //cached value
                subscribe(//filter: field1; sort: field2 desc
                    outgoing,
                    destination,
                    setOf(FieldFilter(fieldName, "x2", FieldOp.CONTAINS)),
                    setOf(FieldOrder(fieldName, OrderKind.DESC))
                )
                (readMessage(incoming) as JsonArray).let { array ->
                    assertEquals(2, array.size)
                    assertEquals(listOf("x22", "x21"), array.map { it.jsonObject[fieldName]?.content })
                }
                subscribe(//sort: field2 desc
                    outgoing,
                    destination,
                    sorts = setOf(FieldOrder(Data::field2.name, OrderKind.DESC), FieldOrder(Data::notSortable.name))
                )
                (readMessage(incoming) as JsonArray).let { array ->
                    assertEquals(message.size, array.size)
                    val expected: List<String> = message.sortedByDescending { it.field2 }.map { it.field1 }
                    assertEquals(expected, array.map { it.jsonObject[fieldName]?.content })
                }
            }

        }
    }

    private suspend fun unsubscribe(
        outgoing: SendChannel<Frame>,
        destination: String
    ) {
        outgoing.send(
            uiMessage(
                Unsubscribe(
                    destination,
                    Subscription.serializer() stringify AgentSubscription(agentId, buildVersion)
                )

            )
        )
    }

    private suspend fun subscribe(
        outgoing: SendChannel<Frame>,
        destination: String,
        filters: Set<FieldFilter> = emptySet(),
        sorts: Set<FieldOrder> = emptySet()
    ) {
        outgoing.send(
            uiMessage(
                Subscribe(
                    destination,
                    Subscription.serializer() stringify AgentSubscription(
                        agentId,
                        buildVersion,
                        filters = filters,
                        orderBy = sorts
                    )
                )
            )
        )
    }

    private suspend fun sendListData(destination: String, message: List<Data>) {
        val ps by kodeinApplication.kodein.instance<PluginSenders>()
        val sender = ps.sender("test-plugin")
        sender.send(AgentSendContext(agentId, buildVersion), destination, message)
    }

    private suspend fun readMessage(incoming: ReceiveChannel<Frame>): JsonElement? {
        val receive = incoming.receive() as? Frame.Text ?: fail()
        val readText = receive.readText()
        val fromJson = json.parseJson(readText) as JsonObject
        return fromJson[WsSendMessage::message.name]
    }

    @Test
    fun `should return data from storage which was sent before via send()`() {
        withTestApplication(testApp) {
            handleWebSocketConversation(socketUrl()) { incoming, outgoing ->
                val destination = "/pluginTopic1"
                val messageForTest = TestMessage("testMessage")
                val wsPluginService by kodeinApplication.instance<PluginSenders>()
                val sender = wsPluginService.sender(pluginId)
                val sendContext = AgentSendContext(agentId, buildVersion)
                @Suppress("DEPRECATION")
                sender.send(sendContext, destination, messageForTest)
                outgoing.send(
                    uiMessage(
                        Subscribe(
                            destination,
                            Subscription.serializer() stringify AgentSubscription(agentId, buildVersion)
                        )
                    )
                )

                val receive = incoming.receive() as? Frame.Text ?: fail()
                val readText = receive.readText()
                val fromJson = JsonObject.serializer() parse readText
                assertEquals(destination, fromJson[WsSendMessage::destination.name]?.content)
                assertEquals(WsMessageType.MESSAGE.name, fromJson["type"]?.content)
                assertEquals(
                    json.toJson(TestMessage.serializer(), messageForTest),
                    fromJson[WsSendMessage::message.name])

                outgoing.send(uiMessage(Subscribe(destination, "")))
            }
        }
    }

    private fun TestApplicationEngine.socketUrl() = "/ws/plugins/$pluginId?token=${requestToken()}"
}
