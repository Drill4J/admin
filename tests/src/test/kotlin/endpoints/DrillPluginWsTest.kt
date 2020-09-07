package com.epam.drill.admin.endpoints

import com.epam.drill.admin.*
import com.epam.drill.admin.cache.*
import com.epam.drill.admin.cache.impl.*
import com.epam.drill.admin.common.*
import com.epam.drill.admin.endpoints.plugin.*
import com.epam.drill.admin.kodein.*
import com.epam.drill.admin.plugin.*
import com.epam.drill.admin.storage.*
import com.epam.drill.admin.websockets.*
import com.epam.drill.common.*
import com.epam.drill.plugin.api.end.*
import com.epam.drill.testdata.*
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
import kotlin.test.*

class PluginWsTest {

    private val pluginId = testPlugin.pluginId

    private val agentId = "testAgent"
    private val buildVersion = "1.0.0"
    private val agentInfo = AgentInfo(
        id = agentId,
        name = "test",
        status = AgentStatus.ONLINE,
        ipAddress = "1.7.2.23",
        environment = "test",
        description = "test",
        agentVersion = "0.0.0-test",
        buildVersion = buildVersion,
        agentType = AgentType.JAVA
    )

    private lateinit var kodeinApplication: Kodein

    private val testApp: Application.() -> Unit = {
        install(Locations)
        install(WebSockets)

        install(ContentNegotiation) {
            json()
        }

        enableSwaggerSupport()
        kodeinApplication = kodeinApplication(AppBuilder {
            withKModule { kodeinModule("pluginServices", pluginServices) }
            withKModule {
                kodeinModule("test") {
                    bind<LoginHandler>() with eagerSingleton {
                        LoginHandler(
                            kodein
                        )
                    }
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
                            AgentSubscription.serializer() stringify AgentSubscription(agentId, buildVersion)
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
    data class Data(val field1: String, val field2: Int)

    @Test
    fun `should apply filters to list topics`() {
        withTestApplication(testApp) {
            val fieldName = Data::field1.name
            handleWebSocketConversation(socketUrl()) { incoming, outgoing ->

                val destination = "/ws/plugins/test-plugin"
                val message = listOf(
                    Data("x1", 10),
                    Data("x2", 10),
                    Data("x2", 10),
                    Data("x3", 10)
                )


                subscribeWithFilter(outgoing, destination)
                assertEquals("", readMessage(incoming)?.content, "first subscription should be empty")


                sendListData(destination, message)
                assertEquals(message.size, (readMessage(incoming) as JsonArray).size)


                subscribeWithFilter(outgoing, destination, SearchStatement(fieldName, "x1"))
                assertEquals(1, (readMessage(incoming) as JsonArray).size)

                unsubscribe(outgoing, destination)

                subscribeWithFilter(outgoing, destination, SearchStatement(fieldName, "x2"))
                assertEquals(2, (readMessage(incoming) as JsonArray).size)

                unsubscribe(outgoing, destination)

                //cache value
                subscribeWithFilter(outgoing, destination, SearchStatement(fieldName, "x2"))
                assertEquals(2, (readMessage(incoming) as JsonArray).size)

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
                    AgentSubscription.serializer() stringify AgentSubscription(agentId, buildVersion)
                )

            )
        )
    }

    private suspend fun subscribeWithFilter(
        outgoing: SendChannel<Frame>,
        destination: String,
        filter: SearchStatement? = null
    ) {
        outgoing.send(
            uiMessage(
                Subscribe(
                    destination,
                    AgentSubscription.serializer() stringify AgentSubscription(agentId, buildVersion, searchStatement = filter)
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
                val messageForTest = "testMessage"
                val wsPluginService by kodeinApplication.instance<PluginSenders>()
                val sender = wsPluginService.sender(pluginId)
                val sendContext = AgentSendContext(agentInfo.id, agentInfo.buildVersion)
                @Suppress("DEPRECATION")
                sender.send(sendContext, destination, messageForTest)
                outgoing.send(
                    uiMessage(
                        Subscribe(
                            destination,
                            AgentSubscription.serializer() stringify AgentSubscription(agentId, buildVersion)
                        )
                    )
                )

                val receive = incoming.receive() as? Frame.Text ?: fail()
                val readText = receive.readText()
                val fromJson = JsonObject.serializer() parse readText
                assertEquals(destination, fromJson[WsSendMessage::destination.name]?.content)
                assertEquals(WsMessageType.MESSAGE.name, fromJson["type"]?.content)
                assertEquals(messageForTest, fromJson[WsSendMessage::message.name]?.content)

                outgoing.send(uiMessage(Subscribe(destination, "")))
            }
        }
    }

    private fun TestApplicationEngine.socketUrl() = "/ws/plugins/$pluginId?token=${requestToken()}"
}
