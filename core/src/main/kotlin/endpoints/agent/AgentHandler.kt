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
import com.epam.drill.admin.router.*
import com.epam.drill.admin.util.*
import com.epam.drill.common.MessageType
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

class AgentHandler(override val di: DI) : DIAware{
    private val logger = KotlinLogging.logger {}

    private val app by instance<Application>()
    private val agentManager by instance<AgentManager>()
    private val buildManager by instance<BuildManager>()
    private val pd by instance<PluginDispatcher>()
    private val topicResolver by instance<TopicResolver>()

    init {
        app.routing {
            agentWebsocket("/agent/attach") {
                val (agentConfig, needSync) = call.request.retrieveParams()
                val frameType = when (agentConfig.agentType) {
                    com.epam.drill.common.AgentType.JAVA -> FrameType.BINARY
                    com.epam.drill.common.AgentType.DOTNET,
                    com.epam.drill.common.AgentType.NODEJS -> FrameType.TEXT
                }
                val agentSession = AgentWsSession(
                    this@agentWebsocket,
                    frameType,
                    application.agentSocketTimeout,
                    agentConfig.id,
                    agentConfig.instanceId
                )
                val agentInfo: AgentInfo = withContext(Dispatchers.IO) {
                    agentManager.attach(agentConfig, needSync, agentSession)
                }
                agentSession.createWsLoop(
                    agentInfo,
                    call.request.headers[ContentEncoding] == "deflate"
                )
            }
        }
    }

    private suspend fun AgentWsSession.createWsLoop(agentInfo: AgentInfo, useCompression: Boolean) {
        val agentDebugStr = agentInfo.debugString(instanceId)
        try {
            val buildData = buildManager.buildData(agentInfo.id)
            incoming.consumeEach { frame ->
                when (frame) {
                    is Frame.Binary -> runCatching {
                        withContext(Dispatchers.IO) {
                            val bytes = frame.readBytes()
                            val frameBytes = if (useCompression) {
                                Zstd.decompress(bytes)
                            } else bytes
                            BinaryMessage(ProtoBuf.load(com.epam.drill.common.Message.serializer(), frameBytes))
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

                            MessageType.START_CLASSES_TRANSFER -> {
                                logger.debug { "Starting classes transfer for $agentDebugStr..." }
                            }

                            MessageType.CLASSES_DATA -> {
                                message.bytes.takeIf { it.isNotEmpty() }?.let { rawBytes ->
                                    ProtoBuf.load(ByteArrayListWrapper.serializer(), rawBytes).bytesList.forEach {
                                        buildData.agentBuildManager.addClass(it)
                                    }
                                }
                            }

                            MessageType.FINISH_CLASSES_TRANSFER -> buildData.apply {
                                initClasses(agentInfo.build.version)
                                topicResolver.sendToAllSubscribed(WsRoutes.AgentBuildsSummary(agentInfo.id))
                                logger.debug { "Finished classes transfer for $agentDebugStr" }
                            }

                            else -> {
                                logger.warn { "Message with type '${message.type}' is not supported yet" }
                            }
                        }
                    } ?: logger.warn { "Not supported frame type: ${frame.frameType}" }
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
}

private fun ApplicationRequest.retrieveParams(): Pair<CommonAgentConfig, Boolean> {
    val configStr = headers[com.epam.drill.common.AgentConfigParam]!!
    val agentConfig = if (configStr.startsWith('{')) {
        CommonAgentConfig.serializer() parse configStr
    } else ProtoBuf.loads(CommonAgentConfig.serializer(), configStr)
    val needSync = headers[com.epam.drill.common.NeedSyncParam]!!.toBoolean()
    return agentConfig to needSync
}
