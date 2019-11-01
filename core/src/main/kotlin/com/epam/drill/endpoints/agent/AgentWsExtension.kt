package com.epam.drill.endpoints.agent

import com.epam.drill.common.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import java.util.concurrent.*
import kotlin.reflect.*
import kotlin.time.*


fun Route.agentWebsocket(path: String, protocol: String? = null, handler: suspend AgentWsSession.() -> Unit) {
    webSocket(path, protocol) {
        handler(AgentWsSession(this))
    }
}

class WsAwaitException(message: String) : RuntimeException(message)

class Signal(var state: Boolean = false, val callback: suspend (Any) -> Unit) {
    suspend fun await(timeout: Duration = 40.seconds) {
        awaitWithExpr(timeout) { state }
    }
}

suspend fun awaitWithExpr(timeout: Duration, delay: Long = 50, state: () -> Boolean) {
    val expirationMark = MonoClock.markNow() + timeout
    while (state()) {
        if (expirationMark.hasPassedNow()) throw WsAwaitException("did't get signal by $timeout")
        delay(delay)
    }
}

class WsDeferred(val asession: AgentWsSession, val topicName: String) {
    inline fun <reified T> then(noinline handler: suspend (T) -> Unit) {
        asession.subscribe(topicName, handler)
    }

    suspend fun await() {
        val q = Signal(true, {})
        @Suppress("UNCHECKED_CAST")
        asession.subscribers[topicName] = q
        q.await()
    }
}


open class AgentWsSession(val session: DefaultWebSocketServerSession) : DefaultWebSocketServerSession by session {

    val subscribers = ConcurrentHashMap<String, Signal>()

    suspend fun sendToTopic(topicName: String, message: Any = ""): WsDeferred {
        @Suppress("UNCHECKED_CAST")
        val kClass = message::class as KClass<Any>
        send(
            Frame.Text(
                Message.serializer() stringify
                        Message(
                            MessageType.MESSAGE, topicName,
                            kClass.serializer() stringify message
                        )
            )
        )
        return WsDeferred(this, topicName)
    }


    suspend fun sendBinary(topicName: String, meta: Any = "", data: ByteArray): WsDeferred {
        sendToTopic(topicName, meta)

        send(Frame.Binary(false, data))
        return WsDeferred(this, topicName)
    }


    inline fun <reified T> subscribe(
        topicName: String,
        noinline handler: suspend (T) -> Unit
    ) {
        @Suppress("UNCHECKED_CAST")
        subscribers[topicName] = Signal(false, (handler as suspend (Any) -> Unit))
    }


}