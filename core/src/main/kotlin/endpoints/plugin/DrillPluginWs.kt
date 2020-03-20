@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.epam.drill.admin.endpoints.plugin

import com.epam.drill.admin.cache.*
import com.epam.drill.admin.cache.type.*
import com.epam.drill.admin.common.*
import com.epam.drill.admin.core.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.common.*
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

    override suspend fun send(agentId: String, buildVersion: String, destination: Any, message: Any) {
        val dest = destination.toDestination(app)
        val id = CacheId(
            agentId = agentId,
            destination = dest,
            buildVersion = buildVersion
        )

        if (isRemoveMessage(message)) {
            logger.info { "Removed message by id $id" }
            eventStorage.remove(id.toString())
            return
        }

        val webSocketMessage = toWebSocketMessage(message, dest)
        sendSubscribes(id, webSocketMessage)
    }

    private fun isRemoveMessage(message: Any) = message.toString().isEmpty()

    @Suppress("UNCHECKED_CAST")
    private fun toWebSocketMessage(message: Any, destination: String): String =
        if (message is List<*>) {
            WsSendMessageListData.serializer() stringify WsSendMessageListData(
                WsMessageType.MESSAGE,
                destination,
                message as List<Any>
            )
        } else {
            WsSendMessage.serializer() stringify WsSendMessage(
                WsMessageType.MESSAGE,
                destination,
                message
            )
        }

    private suspend fun sendSubscribes(cacheId: CacheId, webSocketMessage: String) {
        logger.debug { "Send data to destination=${cacheId.destination}, message=$webSocketMessage" }
        eventStorage[cacheId.toString()] = webSocketMessage
        sessionStorage[cacheId.destination]?.let { sessionDataSet ->
            val subscribeInfo = SubscribeInfo(
                agentId = cacheId.agentId,
                buildVersion = cacheId.buildVersion
            )
            sessionDataSet.forEach { data ->
                try {
                    if (data.subscribeInfo == subscribeInfo) {
                        data.session.send(Frame.Text(webSocketMessage))
                    }
                } catch (ex: Exception) {
                    when (ex) {
                        is ClosedSendChannelException, is CancellationException -> logger.debug { "Channel for websocket ${cacheId.destination} closed" }
                        else -> logger.error(ex) { "Sending data to ${cacheId.destination} destination was finished with exception" }
                    }
                    sessionDataSet.removeIf { it == data }
                }
            }
        } ?: logger.warn { "WS topic '${cacheId.destination}' not registered yet" }
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

                                    val cacheId = CacheId(
                                        agentId = subscribeInfo.agentId,
                                        destination = event.destination,
                                        buildVersion = subscribeInfo.buildVersion
                                    )
                                    val message = eventStorage[cacheId.toString()]

                                    if (message.isNullOrEmpty()) {
                                        val emptyWsMessage = (WsSendMessage.serializer() stringify
                                                WsSendMessage(
                                                    WsMessageType.MESSAGE,
                                                    event.destination,
                                                    ""
                                                )).textFrame()
                                        this.send(emptyWsMessage)
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
                                    close()
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

data class CacheId(
    val agentId: String = "",
    val destination: String = "",
    val buildVersion: String = ""
) {
    override fun toString(): String = "$agentId:$destination:$buildVersion"
}

@Serializable
data class SubscribeInfo(
    val agentId: String = "",
    val serviceGroup: String = "",
    val buildVersion: String = ""
)

data class SessionData(
    val session: DefaultWebSocketServerSession,
    val subscribeInfo: SubscribeInfo
) {
    override fun equals(other: Any?) = other is SessionData && other.session == session
    override fun hashCode() = session.hashCode()
}

private fun Any.toDestination(app: Application): String =
    this as? String ?: app.toLocation(this)
