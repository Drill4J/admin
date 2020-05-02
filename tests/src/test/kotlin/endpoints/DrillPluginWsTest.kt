@file:Suppress("UNUSED_PARAMETER", "FunctionName")

package com.epam.drill.admin.endpoints

import com.epam.drill.admin.*
import com.epam.drill.admin.cache.*
import com.epam.drill.admin.cache.impl.*
import com.epam.drill.admin.common.*
import com.epam.drill.admin.endpoints.plugin.*
import com.epam.drill.admin.kodein.*
import com.epam.drill.admin.storage.*
import com.epam.drill.admin.websockets.*
import com.epam.drill.common.*
import com.epam.drill.plugin.api.end.*
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.cio.websocket.*
import io.ktor.locations.*
import io.ktor.serialization.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.serialization.json.*
import org.kodein.di.*
import org.kodein.di.generic.*
import kotlin.test.*


class PluginWsTest {
    lateinit var kodeinApplication: Kodein
    private val testApp: Application.() -> Unit = {
        install(Locations)
        install(WebSockets)

        install(ContentNegotiation) {
            json()
        }

        enableSwaggerSupport()
        kodeinApplication = kodeinApplication(AppBuilder {
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
                    bind<AgentManager>() with eagerSingleton {
                        AgentManager(
                            kodein
                        )
                    }
                }

            }
        })
    }

    val agentId = "testAgent"
    val buildVersion = "1.0.0"
    val agentInfo = AgentInfo(
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

    @Test
    fun `should return empty message on subscribing without info`() {
        withTestApplication(testApp) {
            val token = requestToken()
            handleWebSocketConversation("/ws/drill-plugin-socket?token=${token}") { incoming, outgoing ->
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
            val token = requestToken()
            handleWebSocketConversation("/ws/drill-plugin-socket?token=${token}") { incoming, outgoing ->
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

    @Test
    fun `should return data from storage which was sent before via send()`() {
        withTestApplication(testApp) {
            val token = requestToken()
            handleWebSocketConversation("/ws/drill-plugin-socket?token=${token}") { incoming, outgoing ->
                val destination = "/pluginTopic1"
                val messageForTest = "testMessage"
                val wsPluginService by kodeinApplication.instance<DrillPluginWs>()
                @Suppress("DEPRECATION")
                wsPluginService.send(agentInfo.id, agentInfo.buildVersion, destination, messageForTest)
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

}
