/**
 * Copyright 2020 - 2022 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("FunctionName")

package com.epam.drill.admin.endpoints

import com.epam.drill.admin.*
import com.epam.drill.admin.auth.config.RoleBasedAuthorization
import com.epam.drill.admin.auth.route.userAuthenticationRoutes
import com.epam.drill.admin.auth.config.securityDiConfig
import com.epam.drill.admin.auth.config.usersDiConfig
import com.epam.drill.admin.cache.*
import com.epam.drill.admin.cache.impl.*
import com.epam.drill.admin.common.*
import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.config.*
import com.epam.drill.admin.di.*
import com.epam.drill.admin.endpoints.admin.*
import com.epam.drill.admin.kodein.*
import com.epam.drill.admin.notification.*
import com.epam.drill.admin.storage.*
import com.epam.drill.admin.websocket.*
import com.epam.drill.e2e.GUEST_USER
import com.epam.drill.e2e.testPluginServices
import com.epam.dsm.test.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.config.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.cio.websocket.*
import io.ktor.locations.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.json.*
import org.kodein.di.*
import org.kodein.di.ktor.closestDI as di
import kotlin.test.*


internal class DrillServerWsTest {

    private lateinit var notificationsManager: NotificationManager
    private val testApp: Application.() -> Unit = {
        (environment.config as MapApplicationConfig).apply {
            put("drill.auth.userRepoType", "ENV")
            put("drill.auth.envUsers", listOf(GUEST_USER))
        }
        install(Locations)
        install(WebSockets)

        install(ContentNegotiation) {
            converters()
        }

        install(RoleBasedAuthorization)

        enableSwaggerSupport()

        TestDatabaseContainer.startOnce()
        hikariConfig = TestDatabaseContainer.createDataSource()
        kodein {
            withKModule { kodeinModule("securityConfig", securityDiConfig) }
            withKModule { kodeinModule("usersConfig", usersDiConfig) }
            withKModule { kodeinModule("pluginServices", testPluginServices()) }
            withKModule { kodeinModule("wsHandler", wsHandler) }
            withKModule {
                kodeinModule("test") {
                    bind<AgentStorage>() with eagerSingleton { AgentStorage() }
                    if (app.drillCacheType == "mapdb") {
                        bind<CacheService>() with eagerSingleton { MapDBCacheService() }
                    } else bind<CacheService>() with eagerSingleton { JvmCacheService() }
                    bind<SessionStorage>() with eagerSingleton { sessionStorage }
                    bind<NotificationManager>() with eagerSingleton {
                        notificationsManager = NotificationManager(di)
                        notificationsManager
                    }
                    bind<NotificationEndpoints>() with eagerSingleton { NotificationEndpoints(di) }
                    bind<AgentManager>() with eagerSingleton { AgentManager(di) }
                    bind<BuildStorage>() with eagerSingleton { BuildStorage() }
                    bind<BuildManager>() with eagerSingleton { BuildManager(di) }
                    bind<ServerStubTopics>() with eagerSingleton { ServerStubTopics(di) }
                    bind<DrillAdminEndpoints>() with eagerSingleton { DrillAdminEndpoints(di) }
                }

            }
        }

        routing {
            route("/api") {
                userAuthenticationRoutes()
            }
        }
    }

    @AfterTest
    fun removeStore() {
        TestDatabaseContainer.clearData()
    }

    private val sessionStorage = SessionStorage()

    @Test
    fun testConversation() {
        withTestApplication(testApp) {
            val token = requestToken()
            handleWebSocketConversation("/ws/drill-admin-socket?token=${token}") { incoming, outgoing ->
                assertEquals(0, sessionStorage.sessionCount())
                assertEquals(0, sessionStorage.destinationCount())
                outgoing.send(uiMessage(Subscribe(locations.href(PainRoutes.MyTopic()), "")))
                val actual = incoming.receive()
                assertNotNull(actual)
                assertEquals(1, sessionStorage.sessionCount())
                assertEquals(1, sessionStorage.destinationCount())
                outgoing.send(uiMessage(Unsubscribe(locations.href(PainRoutes.MyTopic()))))
                outgoing.send(uiMessage(Subscribe(locations.href(PainRoutes.MyTopic()), "")))
                assertNotNull(incoming.receive())
                assertEquals(1, sessionStorage.sessionCount())
                assertEquals(1, sessionStorage.destinationCount())
                outgoing.send(uiMessage(Subscribe(locations.href(PainRoutes.MyTopic2()), "")))
                assertNotNull(incoming.receive())
                assertEquals(1, sessionStorage.sessionCount())
                assertEquals(2, sessionStorage.destinationCount())
            }
        }
        assertEquals(0, sessionStorage.destinationCount())
        assertEquals(0, sessionStorage.sessionCount())
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
                        ApiNotifications.Notification.ToggleStatus(
                            ApiNotifications.Notification(
                                firstNotificationId
                            )
                        )
                    ),
                    token,
                    NotificationStatus.serializer() stringify NotificationStatus(isRead = true)
                )
                currentNotifications = getCurrentNotifications(incoming, outgoing)
                assertTrue(
                    currentNotifications.find { it.id == firstNotificationId }!!.read
                )
            }
        }
    }

    private fun TestApplicationEngine.handleHttpPatchRequest(location: String, token: String, body: String) {
        handleRequest(HttpMethod.Patch, location) {
            addHeader(HttpHeaders.Authorization, "Bearer $token")
            addHeader(HttpHeaders.ContentType, "$Json")
            setBody(body)
        }
    }

    private suspend fun TestApplicationCall.getCurrentNotifications(
        incoming: ReceiveChannel<Frame>,
        outgoing: SendChannel<Frame>,
    ): List<Notification> {
        outgoing.send(uiMessage(Subscribe(locations.href(WsNotifications), "")))
        val frame = incoming.receive()
        val json = (frame as Frame.Text).readText().parseJson() as JsonObject
        outgoing.send(uiMessage(Unsubscribe(locations.href(WsNotifications))))
        return parseNotifications(json)
    }

    private fun parseNotifications(json: JsonObject): List<Notification> {
        return ListSerializer(Notification.serializer()) fromJson json.getValue(WsSendMessage::message.name)
    }

    @Test
    fun `topic resolvation goes correctly`() {
        withTestApplication(testApp) {
            val token = requestToken()
            assertNotNull(token, "token can't be empty")
            handleWebSocketConversation("/ws/drill-admin-socket?token=${token}") { incoming, outgoing ->
                outgoing.send(uiMessage(Subscribe("/blabla/pathOfPain", "")))
                val tmp = incoming.receive()
                assertNotNull(tmp)
                val parseJson = (tmp as Frame.Text).readText().parseJson()
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
            val call = handleWebSocket("/ws/drill-admin-socket?token=${invalidToken}") {}
            assertEquals(401, call.response.status()?.value)
        }
    }

    fun generateNotification(agentId: String) {
        notificationsManager.save(
            Notification(
                id = "id",
                agentId = agentId,
                createdAt = System.currentTimeMillis(),
                type = NotificationType.BUILD,
                message = NewBuildArrivedMessage(
                    "0.2.0",
                    setOf("recommendation_1", "recommendation_2")
                ).toJson()
            )
        )
    }
}

fun uiMessage(message: WsReceiveMessage): Frame.Text = WsReceiveMessage.serializer().run {
    stringify(message).toTextFrame()
}

class ServerStubTopics(override val di: DI) : DIAware {
    private val wsTopic by instance<WsTopic>()

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
