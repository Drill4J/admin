package com.epam.drill.admin.endpoints.agent

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.common.serialization.*
import com.epam.drill.api.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.modules.*
import kotlinx.serialization.protobuf.*
import kotlin.time.*
import kotlin.time.TimeSource.*


fun Route.agentWebsocket(
    path: String,
    protocol: String? = null,
    handler: suspend DefaultWebSocketServerSession.() -> Unit
) = webSocket(path, protocol, handler)

class WsAwaitException(message: String) : RuntimeException(message)

class Signal(
    private val topicName: String,
    val callback: suspend (Any) -> Unit
) {
    private val _state = atomic(true)

    suspend fun received(result: Any) {
        _state.value = false
        callback(result)
    }

    suspend fun await(timeout: Duration) {
        if (_state.value) {
            awaitWithExpr(timeout, topicName) { _state.value }
        }
    }
}

suspend fun awaitWithExpr(timeout: Duration, description: String, state: () -> Boolean) {
    val expirationMark = Monotonic.markNow() + timeout
    while (state()) {
        if (expirationMark.hasPassedNow()) {
            throw WsAwaitException("Haven't received a signal in $timeout for '$description' destination")
        }
        delay(200)
    }
}

class WsDeferred(
    val timeout: Duration,
    topicName: String,
    callback: suspend (Any) -> Unit = {},
    val caller: suspend () -> Unit
) {
    val signal: Signal = Signal(topicName, callback)

    suspend fun call(): WsDeferred = apply { caller() }

    suspend fun await() {
        signal.await(timeout)
    }
}

open class AgentWsSession(
    private val session: WebSocketServerSession,
    val frameType: FrameType,
    private val timeout: Duration
) : WebSocketServerSession by session {

    val subscribers get() = _subscribers.value

    private val _subscribers = atomic(persistentMapOf<String, Signal>())

    suspend inline fun <reified TopicUrl : Any> sendToTopic(
        message: Any = "",
        noinline callback: suspend (Any) -> Unit = {}
    ): WsDeferred = TopicUrl::class.topicUrl().let { topicName ->
        async(topicName, callback) {
            when (frameType) {
                FrameType.BINARY -> Frame.Binary(
                    fin = true,
                    data = ProtoBuf.dump(
                        Message(
                            MessageType.MESSAGE,
                            topicName,
                            data = (message as? String)?.encodeToByteArray() ?: ProtoBuf.dump(
                                ProtoBuf.context.getContextualOrDefault(message),
                                message
                            )
                        )
                    )
                )
                FrameType.TEXT -> Frame.Text(
                    JsonMessage.serializer() stringify message.toJsonMessage(topicName)
                )
                else -> null
            }?.let { send(it) }
        }
    }

    suspend fun async(
        topicName: String,
        callback: suspend (Any) -> Unit = {},
        caller: suspend () -> Unit
    ) = WsDeferred(
        timeout = timeout,
        topicName = topicName,
        callback = callback,
        caller = caller
    ).putSignal(topicName).call()

    private fun WsDeferred.putSignal(topicName: String) = apply {
        _subscribers.update { it.put(topicName, signal) }
    }
}
