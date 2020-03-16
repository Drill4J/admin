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
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.cio.websocket.*
import io.ktor.locations.*
import io.ktor.serialization.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
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
        buildVersion = buildVersion,
        agentType = AgentType.JAVA
    )

    @Test
    fun `should return CloseFrame if we subscribe without SubscribeInfo`() {
        withTestApplication(testApp) {
            val token = requestToken()
            handleWebSocketConversation("/ws/drill-plugin-socket?token=${token}") { incoming, outgoing ->
                outgoing.send(
                    UiMessage(
                        WsMessageType.SUBSCRIBE,
                        "/pluginTopic1",
                        ""
                    )
                )
                val receive = incoming.receive()
                assertTrue(receive is Frame.Close)
                assertEquals(CloseReason.Codes.INTERNAL_ERROR.code, receive.readReason()?.code)
            }
        }
    }


    @Test
    fun `should communicate with pluginWs and return the empty MESSAGE`() {
        withTestApplication(testApp) {
            val token = requestToken()
            handleWebSocketConversation("/ws/drill-plugin-socket?token=${token}") { incoming, outgoing ->
                val destination = "/pluginTopic2"
                outgoing.send(
                    UiMessage(
                        WsMessageType.SUBSCRIBE,
                        destination,
                        SubscribeInfo.serializer() stringify SubscribeInfo(agentId, buildVersion)
                    )
                )
                val receive = incoming.receive() as? Frame.Text ?: fail()
                val readText = receive.readText()
                val fromJson = WsReceiveMessage.serializer() parse readText
                assertEquals(destination, fromJson.destination)
                assertEquals(WsMessageType.MESSAGE, fromJson.type)
                assertTrue { fromJson.message.isEmpty() }
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
                val wsPluginService: DrillPluginWs by kodeinApplication.instance()
                wsPluginService.send(agentInfo.id, agentInfo.buildVersion, destination, messageForTest)
                outgoing.send(
                    UiMessage(
                        WsMessageType.SUBSCRIBE,
                        destination,
                        SubscribeInfo.serializer() stringify SubscribeInfo(agentId, buildVersion)
                    )
                )

                val receive = incoming.receive() as? Frame.Text ?: fail()
                val readText = receive.readText()
                val fromJson = WsReceiveMessage.serializer() parse readText
                assertEquals(destination, fromJson.destination)
                assertEquals(WsMessageType.MESSAGE, fromJson.type)
                assertEquals(messageForTest, fromJson.message)

                outgoing.send(
                    UiMessage(
                        WsMessageType.SUBSCRIBE,
                        destination,
                        ""
                    )
                )
            }
        }
    }

}

