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
package com.epam.drill.admin.websocket

import com.epam.drill.admin.api.websocket.*
import com.epam.drill.admin.common.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.util.*
import io.ktor.http.cio.websocket.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.channels.*
import mu.*
import java.util.concurrent.*

/**
 * Storage of the WebSocket sessions of the Admin UI
 */
class SessionStorage {
    private val logger = KotlinLogging.logger {}

    internal val sessions get() = _sessions.value

    private val _sessions =
        atomic(emptyBiSetMap<Any, WebSocketSession>())

    private val _subscriptions = atomic(
        emptyBiSetMap<String, Subscription>()
    )

    operator fun contains(destination: String): Boolean = destination in sessions.first

    fun destinationCount() = sessions.first.size

    fun sessionCount() = sessions.second.size

    suspend fun sendTo(
        destination: String,
        message: FrontMessage,
        type: WsMessageType = WsMessageType.MESSAGE,
    ): Set<WebSocketSession> = sessions.first[destination].onEach {
        it.send(destination, message.toWsMessageAsString(destination, type))
    }

    /**
     * Send a message to the plugin on the admin UI if it's subscribed to the destination
     * @param destination the destination of the message
     * @param messageProvider the function which provides a message by a subscription
     */
    suspend fun sendTo(
        destination: String,
        messageProvider: (Subscription) -> String,
    ) {
        _subscriptions.value.first[destination].forEach { subscription ->
            sessions.first[destination to subscription].forEach { session ->
                session.send(destination, messageProvider(subscription))
            }
        }
    }

    private suspend fun WebSocketSession.send(
        destination: String,
        messageStr: String,
    ): Unit = try {
        send(messageStr)
        logger.trace { "Sent $messageStr through admin socket" }
    } catch (ex: Exception) {
        when (ex) {
            is ClosedSendChannelException,
            is CancellationException,
            -> logger.debug { "The socket connection was aborted. destination: $destination" }
            else -> logger.error(ex) { "Processing drill ws session was finished with exception. destination: $destination" }
        }
        unsubscribe(destination, this)
        Unit
    }

    fun subscribe(
        subscription: Subscription,
        destination: String,
        session: WebSocketSession,
    ): String = subscription.toKey(destination).also { key ->
        _subscriptions.update { it.put(key, subscription) }
        _sessions.update { it.put(key to subscription, session) }
    }

    fun subscribe(
        destination: String,
        session: WebSocketSession,
    ) = _sessions.update { it.put(destination, session) }

    fun unsubscribe(
        subscription: Subscription,
        destination: String,
        session: WebSocketSession,
    ): String = subscription.toKey(destination).also { key ->
        _subscriptions.update { it.remove(key, subscription) }
        _sessions.update { it.remove(key to subscription, session) }
    }

    fun unsubscribe(
        destination: String,
        session: WebSocketSession,
    ): Boolean = session in _sessions.getAndUpdate {
        it.remove(destination, session)
    }.first[destination]

    fun release(session: WebSocketSession): Boolean = session in _sessions.getAndUpdate {
        it.remove(session)
    }.second
}
