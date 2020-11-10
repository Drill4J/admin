package com.epam.drill.admin.plugin

import com.epam.drill.admin.api.websocket.*
import com.epam.drill.admin.common.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.store.*
import com.epam.drill.admin.websocket.*
import com.epam.drill.plugin.api.end.*
import io.ktor.application.*
import kotlinx.coroutines.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*
import kotlin.time.*

class PluginSenders(override val kodein: Kodein) : KodeinAware {
    private val logger = KotlinLogging.logger {}

    private val app by instance<Application>()
    private val pluginStores by instance<PluginStores>()
    private val pluginCaches by instance<PluginCaches>()
    private val pluginSessions by instance<PluginSessions>()

    fun sender(pluginId: String): Sender = object : Sender {
        override suspend fun send(context: SendContext, destination: Any, message: Any) {
            val dest = destination as? String ?: app.toLocation(destination)
            val subscription = context.toSubscription()
            val messageKey = subscription.toKey(dest)
            val pluginCache = pluginCaches.get(pluginId, subscription, true)

            //TODO replace with normal event removal
            if (message == "") {
                logger.trace { "Removed message by key $messageKey" }
                pluginCache[dest] = ""
                pluginStores[pluginId].let { store ->
                    withContext(Dispatchers.IO) {
                        store.deleteMessage(messageKey)
                    }
                }
            } else {
                logger.trace { "Sending message to $messageKey" }
                pluginStores[pluginId].let { store ->
                    withContext(Dispatchers.IO) {
                        measureTimedValue {
                            store.storeMessage(messageKey, message)
                        }.let {
                            logger.trace { "Stored message (key=$messageKey, size=${it.value}) in ${it.duration}" }
                        }
                    }
                }
                pluginCache.remove(dest)
            }
            pluginSessions[pluginId].sendTo(
                destination = messageKey,
                messageProvider = { sessionSubscription ->
                    message.postProcess(sessionSubscription).toWsMessageAsString(
                        destination = dest,
                        type = WsMessageType.MESSAGE,
                        to = sessionSubscription
                    )
                }
            )
        }
    }
}

internal fun SendContext.toSubscription(): Subscription = when (this) {
    is AgentSendContext -> AgentSubscription(agentId, buildVersion)
    is GroupSendContext -> GroupSubscription(groupId)
    else -> error("Unknown send context $this")
}
