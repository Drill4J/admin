@file:Suppress("FunctionName")

package com.epam.drill.admin.endpoints

import com.epam.drill.admin.*
import com.epam.drill.admin.cache.*
import com.epam.drill.admin.cache.impl.*
import com.epam.drill.admin.common.*
import com.epam.drill.admin.config.*
import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.admin.endpoints.openapi.*
import com.epam.drill.admin.jwt.config.*
import com.epam.drill.admin.kodein.*
import com.epam.drill.admin.notification.*
import com.epam.drill.admin.storage.*
import com.epam.drill.admin.websockets.*
import com.epam.drill.common.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.locations.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.json.*
import org.kodein.di.*
import org.kodein.di.generic.*
import java.util.*
import java.util.concurrent.*
import kotlin.test.*


internal class DrillServerWsTest {
    private lateinit var notificationsManager: NotificationManager
    private val testApp: Application.() -> Unit = {
        install(Locations)
        install(WebSockets)
        install(Authentication) {
            jwt {
                realm = "Drill4J app"
                verifier(JwtConfig.verifier)
                validate {
                    it.payload.getClaim("id").asInt()?.let(userSource::findUserById)
                }
            }
        }

        install(ContentNegotiation) {
            converters()
        }

        enableSwaggerSupport()

        kodeinApplication(AppBuilder {
            withKModule { kodeinModule("wsHandler", wsHandler) }
            withKModule {
                kodeinModule("test") {
                    bind<AgentStorage>() with eagerSingleton { AgentStorage() }
                    bind<CacheService>() with eagerSingleton { JvmCacheService() }
                    bind<SessionStorage>() with eagerSingleton { pluginStorage }
                    bind<NotificationManager>() with eagerSingleton {
                        notificationsManager = NotificationManager(kodein)
                        notificationsManager
                    }
                    bind<NotificationEndpoints>() with eagerSingleton { NotificationEndpoints(kodein) }
                    bind<LoginHandler>() with eagerSingleton { LoginHandler(kodein) }
                    bind<AgentManager>() with eagerSingleton { AgentManager(kodein) }
                    bind<ServerStubTopics>() with eagerSingleton { ServerStubTopics(kodein) }
                    bind<DrillAdminEndpoints>() with eagerSingleton { DrillAdminEndpoints(kodein) }
                }

            }
        })
    }

    private val pluginStorage = Collections.newSetFromMap(ConcurrentHashMap<DrillWsSession, Boolean>())

    @Test
    fun testConversation() {
        withTestApplication(testApp) {
            val token = requestToken()
            handleWebSocketConversation("/ws/drill-admin-socket?token=${token}") { incoming, outgoing ->
                outgoing.send(uiMessage(Subscribe(locations.href(PainRoutes.MyTopic()), "")))
                val actual = incoming.receive()
                assertNotNull(actual)
                assertEquals(1, pluginStorage.size)
                outgoing.send(uiMessage(Unsubscribe(locations.href(PainRoutes.MyTopic()))))
                outgoing.send(uiMessage(Subscribe(locations.href(PainRoutes.MyTopic()), "")))
                assertNotNull(incoming.receive())
                assertEquals(1, pluginStorage.size)
                outgoing.send(uiMessage(Subscribe(locations.href(PainRoutes.MyTopic2()), "")))
                assertNotNull(incoming.receive())
                assertEquals(2, pluginStorage.size)
                assertEquals(2, pluginStorage.map { it.url }.toSet().size)
            }
        }
    }

    @Test
    fun `notifications are displayed and processed correctly`() {
        withTestApplication(testApp) {
            val token = requestToken()
            generateNotification("testId")
            handleWebSocketConversation("/ws/drill-admin-socket?token=${token}") { incoming, outgoing ->
                var currentNotifications = getCurrentNotifications(incoming, outgoing)
                val firstNotificationId = currentNotifications.first().id
                handleHttpPatchRequest(
                    locations.href(
                        ApiNotifications.Notification.Read(
                            ApiNotifications.Notification(
                                firstNotificationId
                            )
                        )
                    ), token
                )
                currentNotifications = getCurrentNotifications(incoming, outgoing)
                assertTrue(
                    currentNotifications.find { it.id == firstNotificationId }!!.read
                )
            }
        }
    }

    private fun TestApplicationEngine.handleHttpPatchRequest(location: String, token: String) {
        handleRequest(HttpMethod.Patch, location) {
            addHeader(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    private suspend fun TestApplicationCall.getCurrentNotifications(
        incoming: ReceiveChannel<Frame>,
        outgoing: SendChannel<Frame>
    ): List<Notification> {
        outgoing.send(uiMessage(Subscribe(locations.href(WsNotifications), "")))
        val frame = incoming.receive()
        val json = json.parseJson((frame as Frame.Text).readText()) as JsonObject
        outgoing.send(uiMessage(Unsubscribe(locations.href(WsNotifications))))
        return Notification.serializer().list parse json[WsSendMessage::message.name].toString()
    }

    @Test
    fun `topic resolvation goes correctly`() {
        withTestApplication(testApp) {
            val token = handleRequest(HttpMethod.Post, "/api/login").run { response.headers[HttpHeaders.Authorization] }
            assertNotNull(token, "token can't be empty")
            handleWebSocketConversation("/ws/drill-admin-socket?token=${token}") { incoming, outgoing ->
                outgoing.send(uiMessage(Subscribe("/blabla/pathOfPain", "")))
                val tmp = incoming.receive()
                assertNotNull(tmp)
                val parseJson = json.parseJson((tmp as Frame.Text).readText())
                val parsed =
                    String.serializer() parse (parseJson as JsonObject)[WsSendMessage::message.name].toString()
                assertEquals("testId", parsed)
            }
        }
    }

    @Test
    fun `get UNAUTHORIZED event if token is invalid`() {
        withTestApplication(testApp) {
            val invalidToken = requestToken() + "1"
            handleWebSocketConversation("/ws/drill-admin-socket?token=${invalidToken}") { incoming, _ ->
                val tmp = incoming.receive()
                assertTrue { tmp is Frame.Text }
                val response = JsonObject.serializer() parse (tmp as Frame.Text).readText()
                assertEquals(WsMessageType.UNAUTHORIZED.name, response[WsSendMessage::type.name]?.content)
            }
        }
    }

    fun generateNotification(agentId: String) {
        notificationsManager.save(
            Notification(
                id = "id",
                agentId = agentId,
                createdAt = System.currentTimeMillis(),
                type = NotificationType.BUILD,
                message = NewBuildArrivedMessage.serializer() stringify
                        NewBuildArrivedMessage(
                            "0.2.0",
                            "0.1.0",
                            BuildDiff(1, 2, 3, 4, 5),
                            listOf("recommendation_1", "recommendation_2")
                        )
            )
        )
    }
}

fun uiMessage(message: WsReceiveMessage): Frame.Text = WsReceiveMessage.serializer().run {
    stringify(message).textFrame()
}

class ServerStubTopics(override val kodein: Kodein) : KodeinAware {
    private val wsTopic: WsTopic by instance()

    init {
        runBlocking {
            wsTopic {
                topic<PainRoutes.SomeData> { payload ->
                    if (payload.data == "string") {
                        "the data is: ${payload.data}"
                    } else {
                        "testId"
                    }
                }
                topic<PainRoutes.MyTopic> {
                    "Topic1 response"
                }
                topic<PainRoutes.MyTopic2> {
                    "Topic2 response"
                }
            }

        }
    }
}

object PainRoutes {
    @Location("/{data}/pathOfPain")
    data class SomeData(val data: String)

    @Location("/mytopic")
    class MyTopic

    @Location("/mytopic2")
    class MyTopic2
}
