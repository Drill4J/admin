package com.epam.drill.admin.endpoints.agent

import com.epam.drill.admin.common.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.util.*
import io.ktor.http.cio.websocket.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.channels.*
import mu.*
import java.util.concurrent.*

private val logger = KotlinLogging.logger {}

class SessionStorage {

    internal val sessions get() = _sessions.value

    private val _sessions = atomic(emptyBiSetMap<String, WebSocketSession>())

    operator fun contains(destination: String): Boolean = destination in sessions.first

    suspend fun sendTo(
        destination: String,
        message: Any,
        type: WsMessageType = WsMessageType.MESSAGE
    ): Set<WebSocketSession> = sendTo(
        destination = destination,
        messageProvider = { message.toWsMessageAsString(destination, type) }
    )

    suspend fun sendTo(
        destination: String,
        messageProvider: () -> String
    ): Set<WebSocketSession> = sessions.first[destination].apply {
        if (any()) {
            val messageStr = messageProvider()
            forEach { it.send(destination, messageStr) }
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
        destination: String,
        session: WebSocketSession
    ) = _sessions.update { it.put(destination, session) }

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

fun SessionStorage.sessionCount() = sessions.second.size

fun SessionStorage.destinationCount() = sessions.first.size
