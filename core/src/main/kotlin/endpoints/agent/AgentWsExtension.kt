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
package com.epam.drill.admin.endpoints.agent

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.util.*
import com.epam.drill.api.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.*
import kotlin.time.*
import kotlin.time.TimeSource.*


fun Route.agentWebsocket(
    path: String,
    protocol: String? = null,
    handler: suspend DefaultWebSocketServerSession.() -> Unit,
) = webSocket(path, protocol, handler)

class WsAwaitException(message: String) : RuntimeException(message)

class Signal(
    private val topicName: String,
    val callback: suspend (Any) -> Unit,
) {
    private val _state = atomic(true)

    suspend fun received(result: Any) {
        _state.value = false
        callback(result)
    }

    suspend fun await(timeout: Duration, agentId: String, instanceId: String) {
        if (_state.value) {
            awaitWithExpr(timeout, agentId, instanceId, topicName) { _state.value }
        }
    }
}

suspend fun awaitWithExpr(
    timeout: Duration,
    agentId: String,
    instanceId: String,
    description: String,
    state: () -> Boolean,
) {
    val expirationMark = Monotonic.markNow() + timeout
    while (state()) {
        if (expirationMark.hasPassedNow()) {
            //TODO remove limited() after EPMDJ-9807
            throw WsAwaitException("Haven't received a signal for Agent(id='$agentId', instanceId='$instanceId') in $timeout for destination '${description.limited()}'")
        }
        delay(200)
    }
}

class WsDeferred(
    val timeout: Duration,
    val agentId: String,
    val instanceId: String,
    val topicName: String,
    callback: suspend (Any) -> Unit = {},
    val caller: suspend () -> Unit,
) {
    val signal: Signal = Signal(topicName, callback)

    suspend fun call() = runCatching { caller() }.onFailure {
        val agentDebugStr = "agent(id=${this.agentId}), topicName=$topicName"
        when (it) {
            is java.util.concurrent.CancellationException -> logger.error {
                "Sending a message to $agentDebugStr was cancelled"
            }
            else -> logger.error(it) { "Error in sending a message to $agentDebugStr" }
        }
    }.getOrNull()

    suspend fun await() {
        signal.await(timeout, agentId, instanceId)
    }
}

open class AgentWsSession(
    private val session: WebSocketServerSession,
    val frameType: FrameType,
    private val timeout: Duration,
    private val agentId: String,
    val instanceId: String,
) : WebSocketServerSession by session {

    val subscribers get() = _subscribers.value

    private val _subscribers = atomic(persistentMapOf<String, Signal>())

    suspend inline fun <reified TopicUrl : Any, reified T> sendToTopic(
        message: T,
        topicName: String = TopicUrl::class.topicUrl(),
        noinline callback: suspend (Any) -> Unit = {},
    ): WsDeferred = async(topicName, callback) {
        when (frameType) {
            FrameType.BINARY -> Frame.Binary(
                fin = true,
                data = ProtoBuf.dump(
                    Message(
                        MessageType.MESSAGE,
                        TopicUrl::class.topicUrl(),
                        data = (message as? String)?.encodeToByteArray() ?: ProtoBuf.dump(serializer(), message)
                    )
                )
            )
            FrameType.TEXT -> Frame.Text(
                JsonMessage.serializer() stringify JsonMessage(
                    type = MessageType.MESSAGE,
                    destination = TopicUrl::class.topicUrl(),
                    text = message as? String ?: (serializer<T>() stringify message)
                )
            )
            else -> null
        }?.let { send(it) }
    }

    suspend fun async(
        topicName: String,
        callback: suspend (Any) -> Unit = {},
        caller: suspend () -> Unit,
    ) = WsDeferred(
        timeout = timeout,
        agentId = agentId,
        instanceId = instanceId,
        topicName = topicName,
        callback = callback,
        caller = caller
    ).putSignal(topicName).apply { call() }

    private fun WsDeferred.putSignal(topicName: String) = apply {
        _subscribers.update { it.put(topicName, signal) }
    }
}
