@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.epam.drill.endpoints.plugin

import com.epam.drill.cache.*
import com.epam.drill.cache.type.*
import com.epam.drill.common.*
import com.epam.drill.core.*
import com.epam.drill.endpoints.*
import com.epam.drill.plugin.api.end.*
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*
import java.util.*
import java.util.concurrent.*

private val logger = KotlinLogging.logger {}

class DrillPluginWs(override val kodein: Kodein) : KodeinAware, Sender {

    private val app: Application by instance()
    private val cacheService: CacheService by instance()
    private val eventStorage: Cache<String, String> by cacheService
    private val sessionStorage: ConcurrentMap<String, MutableSet<SessionData>> = ConcurrentHashMap()
    private val agentManager: AgentManager by instance()

    @Suppress("UNCHECKED_CAST")
    override suspend fun send(agentId: String, buildVersion: String, destination: Any, message: Any) {
        val dest = destination as? String ?: app.toLocation(destination)
        val id = "$agentId:$dest:$buildVersion"

        if (message.toString().isEmpty()) {
            logger.info { "Removed message by id $id" }
            eventStorage.remove(id)
        } else {
            val messageForSend = if (message is List<*>) {
                WsSendMessageListData.serializer() stringify WsSendMessageListData(
                    WsMessageType.MESSAGE,
                    dest,
                    message as List<Any>
                )
            } else {
                WsSendMessage.serializer() stringify WsSendMessage(WsMessageType.MESSAGE, dest, message)
            }
            logger.debug { "Send data to $id destination" }
            eventStorage[id] = messageForSend
            sessionStorage[dest]?.let { sessionDataSet ->
                sessionDataSet.forEach { data ->
                    try {

                        if (data.subscribeInfo == SubscribeInfo(agentId, buildVersion)) {
                            data.session.send(Frame.Text(messageForSend))
                        }
                    } catch (ex: Exception) {
                        when (ex) {
                            is ClosedSendChannelException ->logger.debug { "Channel for websocket $id closed" }
                            else -> logger.error(ex) { "Sending data to $id destination was finished with exception" }
                        }
                        sessionDataSet.removeIf { it == data }
                    }
                }
            } ?: run {
                logger.warn { "WS topic '$dest' not registered yet" }
            }
        }
    }

    init {
        app.routing {
            authWebSocket("/ws/drill-plugin-socket") {
                logger.debug { "New session drill-plugin-socket" }

                incoming.consumeEach { frame ->
                    when (frame) {
                        is Frame.Text -> {
                            val event = WsReceiveMessage.serializer() parse frame.readText()
                            logger.debug { "Receive event with: destination '${event.destination}', type '${event.type}' and message '${event.message}'" }

                            when (event.type) {
                                WsMessageType.SUBSCRIBE -> {
                                    val subscribeInfo = SubscribeInfo.serializer() parse event.message

                                    saveSession(event.destination, subscribeInfo)
                                    val buildVersion = subscribeInfo.buildVersion

                                    val message =
                                        eventStorage[
                                                subscribeInfo.agentId + ":" +
                                                        event.destination + ":" +
                                                        if (buildVersion.isNullOrEmpty()) {
                                                            agentManager.buildVersionByAgentId(subscribeInfo.agentId)
                                                        } else buildVersion
                                        ]

                                    if (message.isNullOrEmpty()) {
                                        this.send(
                                            (WsSendMessage.serializer() stringify
                                                    WsSendMessage(
                                                        WsMessageType.MESSAGE,
                                                        event.destination,
                                                        ""
                                                    )).textFrame()
                                        )
                                    } else {
                                        this.send(Frame.Text(message))
                                    }
                                    logger.debug { "${event.destination} is subscribed" }
                                }

                                WsMessageType.UNSUBSCRIBE -> {
                                    sessionStorage[event.destination]?.let {
                                        it.removeIf { data -> data.session == this }
                                    }
                                    logger.debug { "${event.destination} is unsubscribed" }
                                }
                                else -> {
                                    logger.warn { "Event '${event.type}' is not implemented yet" }
                                    close(RuntimeException("Event '${event.type}' is not implemented yet"))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun DefaultWebSocketServerSession.saveSession(destination: String, subscribeInfo: SubscribeInfo) {
        val sessionSet = sessionStorage.getOrPut(destination) {
            Collections.newSetFromMap(ConcurrentHashMap())
        }
        sessionSet.add(SessionData(this, subscribeInfo))
    }

}

@Serializable
data class SubscribeInfo(
    val agentId: String,
    val buildVersion: String? = null
)

data class SessionData(
    val session: DefaultWebSocketServerSession,
    val subscribeInfo: SubscribeInfo
) {
    override fun equals(other: Any?) = other is SessionData && other.session == session
    override fun hashCode() = session.hashCode()
}
