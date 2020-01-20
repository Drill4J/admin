@file:Suppress("FunctionName")

package com.epam.drill.websockets

import com.epam.drill.admin.*
import com.epam.drill.admin.common.*
import com.epam.drill.admin.cache.*
import com.epam.drill.admin.cache.impl.*
import com.epam.drill.common.*
import com.epam.drill.admin.dataclasses.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.admin.endpoints.openapi.*
import com.epam.drill.admin.jwt.config.*
import com.epam.drill.admin.kodein.*
import com.epam.drill.admin.router.*
import com.epam.drill.admin.storage.*
import com.epam.drill.admin.util.*
import com.epam.drill.admin.websockets.*
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
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import org.kodein.di.*
import org.kodein.di.generic.*
import java.util.*
import java.util.concurrent.*
import kotlin.test.*


internal class DrillServerWsTest {
    private lateinit var notificationsManager: NotificationsManager
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
            register(ContentType.Any, EmptyContentWrapper())
        }

        enableSwaggerSupport()

        kodeinApplication(AppBuilder {
            withKModule { kodeinModule("wsHandler", wsHandler) }
            withKModule {
                kodeinModule("test") {
                    bind<AgentStorage>() with eagerSingleton { AgentStorage() }
                    bind<CacheService>() with eagerSingleton { JvmCacheService() }
                    bind<SessionStorage>() with eagerSingleton { pluginStorage }
                    bind<NotificationsManager>() with eagerSingleton {
                        notificationsManager = NotificationsManager(kodein)
                        notificationsManager
                    }
                    bind<LoginHandler>() with eagerSingleton {
                        LoginHandler(
                            kodein
                        )
                    }
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
                outgoing.send(UiMessage(WsMessageType.SUBSCRIBE, locations.href(PainRoutes.MyTopic()), ""))
                val actual = incoming.receive()
                assertNotNull(actual)
                assertEquals(1, pluginStorage.size)
                outgoing.send(UiMessage(WsMessageType.UNSUBSCRIBE, locations.href(PainRoutes.MyTopic()), ""))
                outgoing.send(UiMessage(WsMessageType.SUBSCRIBE, locations.href(PainRoutes.MyTopic()), ""))
                assertNotNull(incoming.receive())
                assertEquals(1, pluginStorage.size)
                outgoing.send(UiMessage(WsMessageType.SUBSCRIBE, locations.href(PainRoutes.MyTopic2()), ""))
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
            generateThreeNotifications("testId", "testName")
            handleWebSocketConversation("/ws/drill-admin-socket?token=${token}") { incoming, outgoing ->
                var currentNotifications = getCurrentNotifications(incoming, outgoing)
                assertNotificationsCounters(currentNotifications, 3, 3)
                val firstNotificationId = currentNotifications.first().id
                val jsonId = NotificationId.serializer() stringify NotificationId(firstNotificationId)
                handleHttpPostRequest(locations.href(Routes.Api.ReadNotification()), jsonId, token)
                currentNotifications = getCurrentNotifications(incoming, outgoing)
                assertEquals(
                    NotificationStatus.READ,
                    currentNotifications.find { it.id == firstNotificationId }!!.status
                )
                assertNotificationsCounters(currentNotifications, 3, 2)
                handleHttpPostRequest(locations.href(Routes.Api.DeleteNotification()), jsonId, token)
                getCurrentNotifications(incoming, outgoing)
                currentNotifications = getCurrentNotifications(incoming, outgoing)
                assertNull(currentNotifications.find { it.id == firstNotificationId })
                assertNotificationsCounters(currentNotifications, 2, 2)
                handleHttpPostRequest(locations.href(Routes.Api.ReadNotification()), NotificationId.serializer() stringify NotificationId(""), token)
                getCurrentNotifications(incoming, outgoing)
                currentNotifications = getCurrentNotifications(incoming, outgoing)
                assertNotificationsCounters(currentNotifications, 2, 0)
                handleHttpPostRequest(locations.href(Routes.Api.DeleteNotification()), NotificationId.serializer() stringify NotificationId(""), token)
                getCurrentNotifications(incoming, outgoing)
                currentNotifications = getCurrentNotifications(incoming, outgoing)
                assertNotificationsCounters(currentNotifications, 0, 0)
            }
        }
    }

    private fun TestApplicationEngine.handleHttpPostRequest(location: String, body: String, token: String) {
        handleRequest(HttpMethod.Post, location) {
            addHeader(HttpHeaders.Authorization, "Bearer $token")
            setBody(body)
        }
    }

    private fun assertNotificationsCounters(notifications: List<Notification>, total: Int, unread: Int) {
        val notificationsCount = notifications.count()
        val unreadNotificationsCount = notifications.count { it.status == NotificationStatus.UNREAD }
        assertEquals(total, notificationsCount)
        assertEquals(unread, unreadNotificationsCount)
    }

    private suspend fun TestApplicationCall.getCurrentNotifications(
        incoming: ReceiveChannel<Frame>,
        outgoing: SendChannel<Frame>
    ): List<Notification> {
        outgoing.send(UiMessage(WsMessageType.SUBSCRIBE, locations.href(WsRoutes.GetNotifications()), ""))
        val frame = incoming.receive()
        val json = json.parseJson((frame as Frame.Text).readText()) as JsonObject
        outgoing.send(UiMessage(WsMessageType.UNSUBSCRIBE, locations.href(WsRoutes.GetNotifications()), ""))
        return Notification.serializer().list parse json[WsReceiveMessage::message.name].toString()
    }

    @Test
    fun `topic resolvation goes correctly`() {
        withTestApplication(testApp) {
            val token = handleRequest(HttpMethod.Post, "/api/login").run { response.headers[HttpHeaders.Authorization] }
            assertNotNull(token, "token can't be empty")
            handleWebSocketConversation("/ws/drill-admin-socket?token=${token}") { incoming, outgoing ->
                outgoing.send(UiMessage(WsMessageType.SUBSCRIBE, "/blabla/pathOfPain", ""))
                val tmp = incoming.receive()
                assertNotNull(tmp)
                val parseJson = json.parseJson((tmp as Frame.Text).readText())
                val parsed =
                    AgentBuildVersionJson.serializer() parse (parseJson as JsonObject)[WsReceiveMessage::message.name].toString()
                assertEquals("testId", parsed.id)
                assertEquals("blabla", parsed.name)
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
                val response = WsReceiveMessage.serializer() parse (tmp as Frame.Text).readText()
                assertEquals(WsMessageType.UNAUTHORIZED, response.type)
            }
        }
    }

    fun generateThreeNotifications(
        agentId: String,
        agentName: String
    ) {
        var previousVersion = ""

        for (i in 0..2) {
            val buildVersion = UUID.randomUUID().toString()
            notificationsManager.save(
                agentId,
                agentName,
                NotificationType.BUILD,
                NewBuildArrivedMessage.serializer() stringify
                        NewBuildArrivedMessage(
                            buildVersion,
                            previousVersion,
                            "prevAlias",
                            BuildDiff(1, 2, 3, 4, 5),
                            listOf("recommendation_1", "recommendation_2")
                        )
            )
            previousVersion = buildVersion
        }
    }
}

fun UiMessage(type: WsMessageType, destination: String, message: String) =
    (WsSendMessage.serializer() stringify WsSendMessage(
        type,
        destination,
        message
    )).textFrame()


fun AgentMessage(type: MessageType, destination: String, message: String) =
    (Message.serializer() stringify Message(type, destination, message)).textFrame()


class ServerStubTopics(override val kodein: Kodein) : KodeinAware {
    private val wsTopic: WsTopic by instance()

    init {
        runBlocking {
            wsTopic {
                topic<PainRoutes.SomeData> { payload ->
                    if (payload.data == "string") {
                        "the data is: ${payload.data}"
                    } else {
                        AgentBuildVersionJson(
                            id = "testId",
                            name = payload.data
                        )
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
