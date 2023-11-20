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
@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.epam.drill.admin.endpoints.agent

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.agent.AgentInfo
import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.config.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.endpoints.plugin.*
import com.epam.drill.common.agent.configuration.*
import com.epam.drill.common.message.Message
import com.epam.drill.common.message.MessageType
import com.epam.drill.common.util.JavaZip
import io.ktor.application.*
import io.ktor.http.HttpHeaders.ContentEncoding
import io.ktor.http.cio.websocket.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.protobuf.*
import mu.*
import org.kodein.di.*
import org.kodein.di.ktor.closestDI

val logger = KotlinLogging.logger {}

/**
 * Web Socket controller for agents.
 * Used for communication between agents and admin backend via websocket messages.
 */
fun Routing.agentWebSocketRoute() {
    val app by closestDI().instance<Application>()
    val agentManager by closestDI().instance<AgentManager>()
    val buildManager by closestDI().instance<BuildManager>()
    val pd by closestDI().instance<PluginDispatcher>()


    agentWebsocket("/agent/attach") {
        val agentConfig = call.request.retrieveParams()
        val frameType = when (agentConfig.agentType) {
            AgentType.JAVA -> FrameType.BINARY

            AgentType.DOTNET,
            AgentType.NODEJS -> FrameType.TEXT

            else -> FrameType.BINARY
        }
        val agentSession = AgentWsSession(
            this,
            frameType,
            application.agentSocketTimeout,
            agentConfig.id,
            agentConfig.instanceId
        )
        val agentInfo: AgentInfo = withContext(Dispatchers.IO) {
            agentManager.attach(agentConfig, agentSession)
        }
        agentSession.createWsLoop(
            agentInfo,
            call.request.headers[ContentEncoding] == "deflate",
            app,
            pd,
            buildManager
        )
    }
}

/**
 * Receiving messages from agents
 *
 * @features Agent registration, Test running
 */
private suspend fun AgentWsSession.createWsLoop(agentInfo: AgentInfo,
                                                useCompression: Boolean,
                                                app: Application,
                                                pd: PluginDispatcher,
                                                buildManager: BuildManager) {
    val agentDebugStr = agentInfo.debugString(instanceId)
    try {
        incoming.consumeEach { frame ->
            when (frame) {
                is Frame.Binary -> runCatching {
                    withContext(Dispatchers.IO) {
                        val bytes = frame.readBytes()
                        val frameBytes = if (useCompression) {
                            JavaZip.decompress(bytes)
                        } else bytes
                        BinaryMessage(ProtoBuf.load(Message.serializer(), frameBytes))
                    }
                }.onFailure {
                    logger.error(it) { "Error processing $frame." }
                }.getOrNull()
                is Frame.Text -> runCatching {
                    JsonMessage.serializer() parse frame.readText()
                }.onFailure {
                    logger.error(it) { "Error processing $frame." }
                }.getOrNull()
                else -> null
            }?.also { message ->
                withContext(Dispatchers.IO) {
                    logger.trace { "Processing message for $agentDebugStr: $message" }

                    when (message.type) {
                        MessageType.PLUGIN_DATA -> {
                            pd.processPluginData(agentInfo, instanceId, message.text)
                        }
                        MessageType.PLUGIN_ACTION -> {
                            app.launch {
                                pd.dispatchAction(agentInfo, instanceId, message.text)
                            }
                        }
                        MessageType.MESSAGE_DELIVERED -> {
                            subscribers[message.destination]?.received(message) ?: logger.debug {
                                "A subscriber to destination for $agentDebugStr is not found: '${message.destination}'"
                            }
                        }
                        else -> {
                            logger.warn { "Message with type '${message.type}' is not supported yet" }
                        }
                    }
                }
            }
        }
    } catch (ex: Exception) {
        when (ex) {
            is CancellationException -> logger.error { "Handling of $agentDebugStr was cancelled" }
            else -> logger.error(ex) { "Error handling $agentDebugStr" }
        }
    } finally {
        logger.info { "removing instance of $agentDebugStr..." }
        buildManager.removeInstance(agentInfo.toAgentBuildKey(), instanceId)
    }
}

private fun ApplicationRequest.retrieveParams(): CommonAgentConfig {
    val configStr = headers[HEADER_AGENT_CONFIG]!!
    val agentConfig = if (configStr.startsWith('{')) {
        CommonAgentConfig.serializer() parse configStr
    } else ProtoBuf.loads(CommonAgentConfig.serializer(), configStr)
    return agentConfig
}
