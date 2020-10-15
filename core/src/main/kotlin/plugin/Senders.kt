package com.epam.drill.admin.plugin

import com.epam.drill.admin.common.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.endpoints.plugin.*
import com.epam.drill.admin.store.*
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
            val subscriptionKey = subscription.toKey(dest)
            val pluginCache = pluginCaches[pluginId]

            //TODO replace with normal event removal
            if (message == "") {
                logger.trace { "Removed message by key $subscriptionKey" }
                pluginCache[subscriptionKey] = ""
                pluginStores[pluginId].let { store ->
                    withContext(Dispatchers.IO) {
                        store.deleteMessage(subscriptionKey)
                    }
                }
            } else {
                logger.trace { "Sending message to $subscriptionKey" }
                pluginStores[pluginId].let { store ->
                    withContext(Dispatchers.IO) {
                        measureTimedValue {
                            store.storeMessage(subscriptionKey, message)
                        }.let {
                            logger.trace { "Stored message (key=$subscriptionKey, size=${it.value}) in ${it.duration}" }
                        }
                    }
                }
                pluginCache.remove(subscriptionKey)
            }
            pluginSessions[pluginId].sendTo(
                destination = subscriptionKey,
                messageProvider = { sessionSubscription ->
                    message
                        .processWithSubscription(sessionSubscription)
                        .toWsMessageAsString(dest, WsMessageType.MESSAGE, sessionSubscription)
                }
            )
        }
    }
}
