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
package com.epam.drill.admin.endpoints.plugin

import com.epam.drill.admin.api.websocket.*
import com.epam.drill.admin.common.*
import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.core.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.plugin.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.admin.websocket.*
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.channels.*
import mu.*
import org.kodein.di.*
import org.kodein.di.ktor.closestDI

private val logger = KotlinLogging.logger {}

/**
 * WebSocket handler for UI plugin
 * Used for communication between frontend plugins and admin backend via websocket messages.
 */
class DrillPluginWs(override val di: DI) : DIAware {

    private val app by instance<Application>()
    private val pluginCaches by instance<PluginCaches>()
    private val pluginSessions by instance<PluginSessions>()
    private val plugins by instance<Plugins>()
    private val agentManager by instance<AgentManager>()

    fun initRouting(routing: Routing) {
        with(routing) {
            plugins.keys.forEach { pluginId ->
                authWebSocket("/ws/plugins/$pluginId") { handle(pluginId) }
            }
        }
    }

    private suspend fun WebSocketSession.handle(pluginId: String) {
        val session = this
        val sessionCache = pluginSessions[pluginId]
        logger.debug { "plugin $pluginId socket: acquired ${session.toDebugString()}" }
        try {
            @Suppress("EXPERIMENTAL_API_USAGE")
            incoming.consumeEach { frame ->
                when (frame) {
                    is Frame.Text -> {
                        val event = WsReceiveMessage.serializer() parse frame.readText()
                        session.consume(pluginId, event)
                    }
                    else -> logger.error { "Unsupported frame type - ${frame.frameType}!" }
                }
            }
        } catch (e: Exception) {
            when (e) {
                is CancellationException -> logger.debug {
                    "plugin $pluginId session ${session.toDebugString()} was cancelled."
                }
                else -> logger.error(e) {
                    "plugin $pluginId session ${session.toDebugString()} finished with exception."
                }
            }
        } finally {
            sessionCache.release(session)
            logger.debug { "plugin $pluginId socket: released ${session.toDebugString()}" }
        }
    }

    private suspend fun WebSocketSession.consume(
        pluginId: String,
        event: WsReceiveMessage,
    ) {
        logger.trace { "Receiving event $event" }
        val sessionCache = pluginSessions[pluginId]
        when (event) {
            is Subscribe -> {
                val destination = event.destination
                val subscription = event.message.parseSubscription()
                val subscriptionKey = subscription?.let {
                    sessionCache.subscribe(it, destination, this)
                } ?: destination.also { sessionCache.subscribe(it, this) }
                val message = pluginCaches.retrieveMessage(pluginId, subscription, destination)
                val messageToSend = message.postProcessFilter(subscription).toWsMessageAsString(
                    destination,
                    WsMessageType.MESSAGE,
                    subscription
                )
                send(messageToSend)
                logger.trace { "Subscribed to $subscriptionKey, ${toDebugString()}" }
            }
            is Unsubscribe -> {
                val subscriptionKey = event.message.parseSubscription()?.let {
                    sessionCache.unsubscribe(it, event.destination, this)
                } ?: event.destination.also { sessionCache.unsubscribe(it, this) }
                logger.trace { "Unsubscribed from $subscriptionKey, ${toDebugString()}" }
            }
        }
    }

    private fun String.parseSubscription(): Subscription? = takeIf(String::any)?.run {
        Subscription.serializer().parse(this).ensureBuildVersion()
    }

    private fun Subscription.ensureBuildVersion() = if (this is AgentSubscription && buildVersion == null) {
        copy(buildVersion = agentManager.buildVersionByAgentId(agentId))
    } else this
}

fun Routing.pluginWebSocketRoute() {
    val handler by closestDI().instance<DrillPluginWs>()

    handler.initRouting(this)
}
