package com.epam.drill.admin.endpoints.agent

import com.epam.drill.api.*
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
    val expirationMark = kotlin.time.TimeSource.Monotonic.markNow() + timeout
    while (state()) {
        if (expirationMark.hasPassedNow()) throw WsAwaitException("did't get signal by $timeout for '$description' destination")
        delay(200)
    }
}

class WsDeferred(asession: AgentWsSession, val clb: suspend () -> Unit, topicName: String) {
    var signal: Signal = Signal(true, {}, topicName)

    init {
        @Suppress("UNCHECKED_CAST")
        asession.subscribers[topicName] = signal
    }

    suspend fun call(): WsDeferred {
        clb()
        return this
    }

    suspend fun await() {
        signal.await()
    }
}


open class AgentWsSession(val session: DefaultWebSocketServerSession) : DefaultWebSocketServerSession by session {

    val subscribers = ConcurrentHashMap<String, Signal>()

    suspend inline fun <reified TopicUrl : Any> sendToTopic(message: Any = ""): WsDeferred {
        val topicName = TopicUrl::class.topicUrl()
        val callback: suspend () -> Unit = {
            @Suppress("UNCHECKED_CAST")
            val kClass = message::class as KClass<Any>
            val text = Message.serializer() stringify Message(
                MessageType.MESSAGE, topicName,
                if (message is String) message else kClass.serializer() stringify message
            )
            send(Frame.Text(text))
        }
        return WsDeferred(this, callback, topicName).apply { call() }
    }


    suspend inline fun <reified TopicUrl : Any> sendBinary(meta: Any = "", data: ByteArray): WsDeferred {
        sendToTopic<TopicUrl>(meta)
        return WsDeferred(this, { send(Frame.Binary(false, data)) }, TopicUrl::class.topicUrl()).apply { call() }
    }


    inline fun <reified T> subscribe(
        topicName: String,
        noinline handler: suspend (T) -> Unit
    ) {
        @Suppress("UNCHECKED_CAST")
        subscribers[topicName] = Signal(false, (handler as suspend (Any) -> Unit), topicName)
    }


}
