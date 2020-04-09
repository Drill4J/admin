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
        val subscriptionKey = subscription.toKey(dest)

        //TODO replace with normal event removal
        if (message == "") {
            logger.info { "Removed message by key $subscriptionKey" }
            eventStorage.remove(subscriptionKey)
        } else {
            val messageForSend = message.toWsMessageAsString(dest, WsMessageType.MESSAGE)
            logger.debug { "Sending message to $subscriptionKey" }
            eventStorage[subscriptionKey] = messageForSend
            sessionStorage.sendTo(
                destination = subscriptionKey,
                messageProvider = { messageForSend }
            )
        }
    }

    private suspend fun WebSocketSession.consume(event: WsReceiveMessage) {
        logger.debug { "Receiving event $event" }

        when (event) {
            is Subscribe -> {
                val subscriptionKey = event.message.toSubscriptionKey(event.destination)
                sessionStorage.subscribe(subscriptionKey, this)

                val message: String? = eventStorage[subscriptionKey]
                val messageToSend = if (message.isNullOrEmpty()) {
                    WsSendMessage.serializer().stringify(
                        WsSendMessage(
                            type = WsMessageType.MESSAGE,
                            destination = event.destination
                        )
                    )
                } else message
                send(messageToSend)
                logger.debug { "Subscribed to $subscriptionKey, ${toDebugString()}" }
            }
            is Unsubscribe -> {
                val subscriptionKey = event.message.toSubscriptionKey(event.destination)
                sessionStorage.unsubscribe(subscriptionKey, this)
                logger.debug { "Unsubscribed from $subscriptionKey, ${toDebugString()}" }
            }
        }
    }

    private fun String.toSubscriptionKey(dest: String): String = when (this) {
        "" -> dest
        else -> toSubscription().toKey(dest)
    }

    private fun String.toSubscription(): Subscription = (JsonObject.serializer() parse this).let { json ->
        Subscription.serializer().takeIf {
            "type" in json
        } ?: AgentSubscription.serializer()
    }.parse(this).ensureBuildVersion()

    private fun Subscription.ensureBuildVersion() = if (this is AgentSubscription && buildVersion == null) {
        copy(buildVersion = agentManager.buildVersionByAgentId(agentId))
    } else this
}
