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
import io.ktor.utils.io.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.json.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*

private val logger = KotlinLogging.logger {}

class DrillPluginWs(override val kodein: Kodein) : KodeinAware, Sender {

    private val app by instance<Application>()
    private val agentManager by instance<AgentManager>()
    private val cacheService by instance<CacheService>()
    private val eventStorage: Cache<Any, Any> by cacheService

    private val sessionStorage = SessionStorage()

    init {
        app.routing {
            val socketName = "drill-plugin-socket"
            authWebSocket("/ws/$socketName") {
                val session = this
                logger.debug { "$socketName: acquired ${session.toDebugString()}" }
                try {
                    @Suppress("EXPERIMENTAL_API_USAGE")
                    incoming.consumeEach { frame ->
                        when (frame) {
                            is Frame.Text -> {
                                val event = WsReceiveMessage.serializer() parse frame.readText()
                                session.consume(event)
                            }
                            else -> logger.error { "Unsupported frame type - ${frame.frameType}!" }
                        }
                    }
                } catch (e: Exception) {
                    when(e) {
                        is CancellationException -> logger.debug {
                            "$socketName: ${session.toDebugString()} was cancelled."
                        }
                        else -> logger.error(e) {
                            "$socketName: ${session.toDebugString()} finished with exception."
                        }
                    }
                }  finally {
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
            logger.trace { "Removed message by key $subscriptionKey" }
            eventStorage.remove(subscriptionKey)
        } else {
            val messageForSend = message.toWsMessageAsString(dest, WsMessageType.MESSAGE, subscription)
            logger.trace { "Sending message to $subscriptionKey" }
            eventStorage[subscriptionKey] = message
            sessionStorage.sendTo(
                destination = subscriptionKey,
                messageProvider = { messageForSend }
            )
        }
    }

    private suspend fun WebSocketSession.consume(event: WsReceiveMessage) {
        logger.trace { "Receiving event $event" }

        when (event) {
            is Subscribe -> {
                val subscription = event.message.parseSubscription()
                val destination = event.destination
                val subscriptionKey = destination.toKey(subscription)
                sessionStorage.subscribe(subscriptionKey, this)
                val message = eventStorage[subscriptionKey] ?: ""
                val messageToSend = message.toWsMessageAsString(destination, WsMessageType.MESSAGE, subscription)
                send(messageToSend)
                logger.trace { "Subscribed to $subscriptionKey, ${toDebugString()}" }
            }
            is Unsubscribe -> {
                val subscription = event.message.parseSubscription()
                val subscriptionKey = event.destination.toKey(subscription)
                sessionStorage.unsubscribe(subscriptionKey, this)
                logger.trace { "Unsubscribed from $subscriptionKey, ${toDebugString()}" }
            }
        }
    }

    private fun String.toKey(
        subscription: Subscription?
    ): String = subscription?.toKey(this) ?: this

    private fun String.parseSubscription(): Subscription? = takeIf { it.any() }?.run {
        (JsonObject.serializer() parse this).let { json ->
            Subscription.serializer().takeIf {
                "type" in json
            } ?: AgentSubscription.serializer()
        }.parse(this).ensureBuildVersion()
    }

    private fun Subscription.ensureBuildVersion() = if (this is AgentSubscription && buildVersion == null) {
        copy(buildVersion = agentManager.buildVersionByAgentId(agentId))
    } else this
}
