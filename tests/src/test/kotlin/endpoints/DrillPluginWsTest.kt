/**
 * Copyright 2020 EPAM Systems
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
package com.epam.drill.admin.endpoints

import com.epam.drill.admin.*
import com.epam.drill.admin.api.websocket.*
import com.epam.drill.admin.cache.*
import com.epam.drill.admin.cache.impl.*
import com.epam.drill.admin.common.*
import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.config.*
import com.epam.drill.admin.endpoints.plugin.*
import com.epam.drill.admin.endpoints.system.*
import com.epam.drill.admin.kodein.*
import com.epam.drill.admin.plugin.*
import com.epam.drill.admin.storage.*
import com.epam.drill.plugin.api.end.*
import com.epam.drill.testdata.*
import com.epam.dsm.*
import com.zaxxer.hikari.*
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
import org.testcontainers.containers.*
import org.testcontainers.containers.wait.strategy.*
import java.io.*
import java.util.*
import kotlin.test.*

class PluginWsTest {

    @Serializable
    data class TestMessage(val message: String)

    private val pluginId = testPlugin.pluginId

    private val agentId = "testAgent"
    private val buildVersion = "1.0.0"

    private val storageDir = File("build/tmp/test/${this::class.simpleName}-${UUID.randomUUID()}")

    lateinit var postgresContainer: PostgreSQLContainer<Nothing>
    private lateinit var kodeinApplication: Kodein

    private val testApp: Application.() -> Unit = {
        install(Locations)
        install(WebSockets)

        install(ContentNegotiation) {
            json()
        }

        enableSwaggerSupport()
        postgresContainer = PostgreSQLContainer<Nothing>("postgres:12").apply {
            withDatabaseName("dbName")
            withExposedPorts(PostgreSQLContainer.POSTGRESQL_PORT)
            waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2))
            start()
        }
        println("started container with id ${postgresContainer.containerId}.")
        DatabaseFactory.init(HikariDataSource(HikariConfig().apply {
            this.driverClassName = postgresContainer.driverClassName
            this.jdbcUrl = postgresContainer.jdbcUrl
            this.username = postgresContainer.username
            this.password = postgresContainer.password
            this.maximumPoolSize = 3
            this.isAutoCommit = false
            this.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            this.validate()
        }))
        kodeinApplication = kodeinApplication(AppBuilder {

            withKModule { kodeinModule("pluginServices", pluginServices) }
            withKModule {
                kodeinModule("test") {
                    bind<LoginEndpoint>() with eagerSingleton { LoginEndpoint(instance()) }
                    bind<DrillPluginWs>() with eagerSingleton { DrillPluginWs(kodein) }
                    bind<WsTopic>() with singleton {
                        WsTopic(
                            kodein
                        )
                    }
                    if (app.drillCacheType == "mapdb") {
                        bind<CacheService>() with eagerSingleton { MapDBCacheService() }
                    } else bind<CacheService>() with eagerSingleton { JvmCacheService() }
                    bind<AgentStorage>() with eagerSingleton { AgentStorage() }
                    bind<AgentManager>() with eagerSingleton { AgentManager(kodein) }
                }

            }
        })
    }

    @AfterTest
    fun removeStore() {
        storageDir.deleteRecursively()
        postgresContainer.stop()
    }

    @Test
    fun `should return empty message on subscribing without info`() {
        withTestApplication(testApp) {
            handleWebSocketConversation(socketUrl()) { incoming, outgoing ->
                val destination = "/pluginTopic1"
                outgoing.send(uiMessage(Subscribe(destination)))
                val receive = incoming.receive() as? Frame.Text ?: fail()
                val readText = receive.readText()
                val fromJson = readText.parseJson() as JsonObject
                assertEquals(destination, fromJson[WsSendMessage::destination.name]?.toContentString())
                assertEquals(WsMessageType.MESSAGE.name, fromJson["type"]?.toContentString())
                assertEquals("", fromJson[WsSendMessage::message.name]?.toContentString().orEmpty())
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
                val fromJson = readText.parseJson() as JsonObject
                assertEquals(destination, fromJson[WsSendMessage::destination.name]?.toContentString())
                assertEquals(WsMessageType.MESSAGE.name, fromJson["type"]?.toContentString())
                assertEquals("", fromJson[WsSendMessage::message.name]?.toContentString().orEmpty())
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
                assertEquals("", readMessageJson(incoming)?.toContentString().orEmpty(), "first subscription should be empty")


                sendListData(destination, message)
                assertEquals(message.size, (readMessageJson(incoming) as JsonArray).size)


                subscribe(outgoing, destination, filters = setOf(FieldFilter(fieldName, "x11")))
                assertEquals(1, (readMessageJson(incoming) as JsonArray).size)

                unsubscribe(outgoing, destination)

                subscribe(
                    outgoing,
                    destination,
                    output = OutputType.LIST,
                    filters = setOf(FieldFilter(fieldName, "x2", FieldOp.CONTAINS)),
                    orderBy = setOf(FieldOrder(fieldName))
                )
                (readMessageJson(incoming) as JsonObject).let { jsonObj ->
                    val array = jsonObj[ListOutput::items.name] as JsonArray
                    assertEquals(message.size, (jsonObj[ListOutput::totalCount.name] as JsonPrimitive).int)
                    assertEquals(2, (jsonObj[ListOutput::filteredCount.name] as JsonPrimitive).int)
                    assertEquals(2, array.size)
                    assertEquals(listOf("x21", "x22"), array.map { it.jsonObject[fieldName]?.toContentString() })
                }

                unsubscribe(outgoing, destination)
                //cached value
                subscribe(//filter: field1; sort: field2 desc
                    outgoing,
                    destination,
                    filters = setOf(FieldFilter(fieldName, "x2", FieldOp.CONTAINS)),
                    orderBy = setOf(FieldOrder(fieldName, OrderKind.DESC))
                )
                (readMessageJson(incoming) as JsonArray).let { array ->
                    assertEquals(2, array.size)
                    assertEquals(listOf("x22", "x21"), array.map { it.jsonObject[fieldName]?.toContentString() })
                }
                subscribe(//sort: field2 desc
                    outgoing,
                    destination,
                    orderBy = setOf(FieldOrder(Data::field2.name, OrderKind.DESC), FieldOrder(Data::notSortable.name))
                )
                (readMessageJson(incoming) as JsonArray).let { array ->
                    assertEquals(message.size, array.size)
                    val expected: List<String> = message.sortedByDescending { it.field2 }.map { it.field1 }
                    assertEquals(expected, array.map { it.jsonObject[fieldName]?.toContentString() })
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
        output: OutputType = OutputType.DEFAULT,
        filters: Set<FieldFilter> = emptySet(),
        orderBy: Set<FieldOrder> = emptySet()
    ) {
        outgoing.send(
            uiMessage(
                Subscribe(
                    destination,
                    Subscription.serializer() stringify AgentSubscription(
                        agentId,
                        buildVersion,
                        output = output,
                        filters = filters,
                        orderBy = orderBy
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

    private suspend fun readMessageJson(incoming: ReceiveChannel<Frame>): JsonElement? {
        val receive = incoming.receive() as? Frame.Text ?: fail()
        val readText = receive.readText()
        val fromJson = readText.parseJson() as JsonObject
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
                assertEquals(destination, fromJson[WsSendMessage::destination.name]?.toContentString())
                assertEquals(WsMessageType.MESSAGE.name, fromJson["type"]?.toContentString())
                assertEquals(
                    TestMessage.serializer() toJson messageForTest,
                    fromJson[WsSendMessage::message.name])

                outgoing.send(uiMessage(Subscribe(destination, "")))
            }
        }
    }

    private fun TestApplicationEngine.socketUrl() = "/ws/plugins/$pluginId?token=${requestToken()}"
}
