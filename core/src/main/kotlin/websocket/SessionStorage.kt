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
        type: WsMessageType = WsMessageType.MESSAGE
    ): Set<WebSocketSession> = sessions.first[destination].apply {
        forEach {
            it.send(destination, message.toWsMessageAsString(destination, type))
        }
    }

    suspend fun sendTo(
        destination: String,
        messageProvider: (Subscription) -> String
    ) {
        _subscriptions.value.first[destination].forEach { subscription ->
            sessions.first[destination to subscription].forEach { session ->
                session.send(destination, messageProvider(subscription))
            }
        }
    }

    private suspend fun WebSocketSession.send(
        destination: String,
        messageStr: String
    ): Unit = try {
        send(messageStr)
        logger.trace { "Sent $messageStr through admin socket" }
    } catch (ex: Exception) {
        when (ex) {
            is ClosedSendChannelException,
            is CancellationException -> logger.warn { "The socket connection was aborted" }
            else -> logger.error(ex) { "Processing drill ws session was finished with exception" }
        }
        unsubscribe(destination, this)
        Unit
    }

    fun subscribe(
        subscription: Subscription,
        destination: String,
        session: WebSocketSession
    ): String = subscription.toKey(destination).also { key ->
        _subscriptions.update { it.put(key, subscription) }
        _sessions.update { it.put(key to subscription, session) }
    }

    fun subscribe(
        destination: String,
        session: WebSocketSession
    ) = _sessions.update { it.put(destination, session) }

    fun unsubscribe(
        subscription: Subscription,
        destination: String,
        session: WebSocketSession
    ): String = subscription.toKey(destination).also { key ->
        _subscriptions.update { it.remove(key, subscription) }
        _sessions.update { it.remove(key to subscription, session) }
    }

    fun unsubscribe(
        destination: String,
        session: WebSocketSession
    ): Boolean = session in _sessions.getAndUpdate {
        it.remove(destination, session)
    }.first[destination]

    fun release(session: WebSocketSession): Boolean = session in _sessions.getAndUpdate {
        it.remove(session)
    }.second
}
