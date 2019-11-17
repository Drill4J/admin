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

class Signal(
    var state: Boolean = false,
    val callback: suspend (Any) -> Unit,
    private val topicName: String
) {
    suspend fun await(timeout: Duration = 40.seconds) {
        awaitWithExpr(timeout, topicName) { state }
    }
}

suspend fun awaitWithExpr(timeout: Duration, description: String, state: () -> Boolean) {
    val expirationMark = MonoClock.markNow() + timeout
    while (state()) {
        if (expirationMark.hasPassedNow()) throw WsAwaitException("did't get signal by $timeout for '$description' destination")
        delay(200)
    }
}

class WsDeferred(val asession: AgentWsSession, val clb: suspend () -> Unit, val topicName: String) {
    inline fun <reified T> then(noinline handler: suspend (T) -> Unit) {
        asession.subscribe(topicName, handler)
    }

    suspend fun call() {
        clb()
    }

    suspend fun await() {
        val q = Signal(true, {}, topicName)
        @Suppress("UNCHECKED_CAST")
        asession.subscribers[topicName] = q
        clb()
        q.await()
    }
}


open class AgentWsSession(val session: DefaultWebSocketServerSession) : DefaultWebSocketServerSession by session {

    val subscribers = ConcurrentHashMap<String, Signal>()

    suspend fun sendToTopic(topicName: String, message: Any = ""): WsDeferred {
        val callback: suspend () -> Unit = {
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
        }
        return WsDeferred(this, callback, topicName)
    }


    suspend fun sendBinary(topicName: String, meta: Any = "", data: ByteArray): WsDeferred {
        sendToTopic(topicName, meta).call()
        return WsDeferred(this, { send(Frame.Binary(false, data)) }, topicName)
    }


    inline fun <reified T> subscribe(
        topicName: String,
        noinline handler: suspend (T) -> Unit
    ) {
        @Suppress("UNCHECKED_CAST")
        subscribers[topicName] = Signal(false, (handler as suspend (Any) -> Unit), topicName)
    }


}