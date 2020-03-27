@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.epam.drill.admin.endpoints.plugin

import com.epam.drill.admin.cache.*
import com.epam.drill.admin.cache.type.*
import com.epam.drill.admin.common.*
import com.epam.drill.admin.core.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.util.*
import com.epam.drill.common.*
import com.epam.drill.plugin.api.end.*
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.json.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*
import java.util.concurrent.*

private val logger = KotlinLogging.logger {}

class DrillPluginWs(override val kodein: Kodein) : KodeinAware, Sender {

    private val app: Application by instance()
    private val agentManager: AgentManager by instance()
    private val cacheService: CacheService by instance()
    private val eventStorage: Cache<Any, String> by cacheService

    private val _sessions = atomic(emptyBiSetMap<String, WebSocketSession>())

    init {
        app.routing {
            authWebSocket("/ws/drill-plugin-socket") {
                logger.debug { "New session(hash=${hashCode()}) drill-plugin-socket" }
                try {
                    incoming.consumeEach { frame ->
                        when (frame) {
                            is Frame.Text -> {
                                val event = WsReceiveMessage.serializer() parse frame.readText()
                                consume(event)
                            }
                            else -> logger.error { "Unsupported frame type - ${frame.frameType}!" }
                        }
                    }
                } finally {
                    removeFromCache()
                    logger.debug { "Removed session(hash=${hashCode()}) of drill-plugin-socket" }
                }
            }
        }
    }

    override suspend fun send(context: SendContext, destination: Any, message: Any) {
        val dest = destination as? String ?: app.toLocation(destination)
        val subscription = context.toSubscription()
        val id = subscription.toKey(dest)

        //TODO replace with normal event removal
        if (message == "") {
            logger.info { "Removed message by key $id" }
            eventStorage.remove(id)
        } else {
            val messageForSend = if (message is List<*>) {
                @Suppress("UNCHECKED_CAST")
                val listMessage = message as List<Any>
                WsSendMessageListData.serializer() stringify WsSendMessageListData(
                    WsMessageType.MESSAGE,
                    dest,
                    listMessage
                )
            } else {
                WsSendMessage.serializer() stringify WsSendMessage(
                    WsMessageType.MESSAGE,
                    dest,
                    message
                )
            }
            logger.debug { "Send data to $id destination" }
            eventStorage[id] = messageForSend
            val sessions = _sessions.value.first[dest]
            if (sessions.any()) {
                sessions.forEach { it.send(dest, messageForSend)  }
            } else logger.warn { "WS topic '$dest' not registered yet" }
        }
    }

    private suspend fun WebSocketSession.send(
        dest: String,
        message: String
    ) = try {
        send(Frame.Text(message))
    } catch (ex: Exception) {
        when (ex) {
            is ClosedSendChannelException,
            is CancellationException -> logger.debug { "Sending data to $dest was cancelled." }
            else -> logger.error(ex) { "Sending data to $dest finished with exception." }
        }
        unsubscribe(dest)
    }

    private suspend fun WebSocketSession.consume(event: WsReceiveMessage) {
        logger.debug { "Receiving event $event" }

        when (event) {
            is Subscribe -> {
                //TODO remove type field existence check after changes on front end
                val subscription: Subscription = (JsonObject.serializer() parse event.message)
                    .let { json ->
                        Subscription.serializer().takeIf {
                            "type" in json
                        } ?: AgentSubscription.serializer()
                    }.parse(event.message).ensureBuildVersion()
                _sessions.update { it.put(event.destination, this) }

                val message: String? = eventStorage[subscription.toKey(event.destination)]
                val messageToSend = if (message.isNullOrEmpty()) {
                    WsSendMessage.serializer().stringify(
                        WsSendMessage(
                            type = WsMessageType.MESSAGE,
                            destination = event.destination
                        )
                    )
                } else message
                send(messageToSend.textFrame())
                logger.debug { "${event.destination} is subscribed" }
            }
            is Unsubscribe -> unsubscribe(event.destination)
        }
    }

    private fun WebSocketSession.unsubscribe(destination: String) {
        _sessions.update { it.remove(destination, this) }
    }

    private fun WebSocketSession.removeFromCache() {
        _sessions.update { it.remove(this) }
    }

    private fun Subscription.ensureBuildVersion() = if (this is AgentSubscription && buildVersion == null) {
        copy(buildVersion = agentManager.buildVersionByAgentId(agentId))
    } else this
}
