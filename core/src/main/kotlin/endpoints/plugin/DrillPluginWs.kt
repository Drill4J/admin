@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.epam.drill.admin.endpoints.plugin

import com.epam.drill.admin.cache.*
import com.epam.drill.admin.cache.type.*
import com.epam.drill.admin.common.*
import com.epam.drill.admin.core.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.common.*
import com.epam.drill.plugin.api.end.*
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.json.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*

private val logger = KotlinLogging.logger {}

class DrillPluginWs(override val kodein: Kodein) : KodeinAware, Sender {

    private val app: Application by instance()
    private val agentManager: AgentManager by instance()
    private val cacheService: CacheService by instance()
    private val eventStorage: Cache<Any, String> by cacheService

    private val sessionStorage = SessionStorage()

    init {
        app.routing {
            val socketName = "drill-plugin-socket"
            authWebSocket("/ws/$socketName") {
                val session = this
                logger.debug { "$socketName: acquired ${session.toDebugString()}" }
                try {
                    incoming.consumeEach { frame ->
                        when (frame) {
                            is Frame.Text -> {
                                val event = WsReceiveMessage.serializer() parse frame.readText()
                                session.consume(event)
                            }
                            else -> logger.error { "Unsupported frame type - ${frame.frameType}!" }
                        }
                    }
                } finally {
                    sessionStorage.release(session)
                    logger.debug { "$socketName: released ${session.toDebugString()}" }
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
            val messageForSend = message.toWsMessageAsString(dest, WsMessageType.MESSAGE)
            logger.debug { "Send data to $id destination" }
            eventStorage[id] = messageForSend
            sessionStorage.sendTo(
                destination = dest,
                messageProvider = { messageForSend }
            )
        }
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
                sessionStorage.subscribe(event.destination, this)

                val message: String? = eventStorage[subscription.toKey(event.destination)]
                val messageToSend = if (message.isNullOrEmpty()) {
                    WsSendMessage.serializer().stringify(
                        WsSendMessage(
                            type = WsMessageType.MESSAGE,
                            destination = event.destination
                        )
                    )
                } else message
                send(messageToSend)
                logger.debug { "Subscribed to ${event.destination}, ${toDebugString()}" }
            }
            is Unsubscribe -> {
                sessionStorage.unsubscribe(event.destination, this)
                logger.debug { "Unsubscribed from ${event.destination}, ${toDebugString()}" }
            }
        }
    }

    private fun Subscription.ensureBuildVersion() = if (this is AgentSubscription && buildVersion == null) {
        copy(buildVersion = agentManager.buildVersionByAgentId(agentId))
    } else this
}
