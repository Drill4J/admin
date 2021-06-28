/**
 * Copyright 2020 EPAM Systems
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
import org.kodein.di.generic.*

class AgentHandler(override val kodein: Kodein) : KodeinAware {
    private val logger = KotlinLogging.logger {}

    private val app by instance<Application>()
    private val agentManager by instance<AgentManager>()
    private val pd by instance<PluginDispatcher>()
    private val topicResolver by instance<TopicResolver>()

    init {
        app.routing {
            agentWebsocket("/agent/attach") {
                val (agentConfig, needSync) = call.request.retrieveParams()
                val frameType = when (agentConfig.agentType) {
                    com.epam.drill.common.AgentType.NODEJS -> FrameType.TEXT
                    else -> FrameType.BINARY
                }
                val agentSession = AgentWsSession(
                    this@agentWebsocket,
                    frameType,
                    application.agentSocketTimeout,
                    agentConfig.id,
                    agentConfig.instanceId
                )
                val agentInfo: AgentInfo = agentConfig.toAgentInfo().takeIf {
                    agentManager.isInstanceIgnored(agentConfig.id, agentConfig.instanceId)
                }?.also {
                    logger.info { "Instance ${agentConfig.instanceId} of ${agentConfig.id} is ignored " }
                } ?: withContext(Dispatchers.IO) {
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
            val adminData = agentManager.adminData(agentInfo.id)
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
                        logger.trace { "Processing message $message." }

                        when (message.type) {
                            MessageType.PLUGIN_DATA -> {
                                pd.processPluginData(agentInfo, instanceId, message.text)
                            }

                            MessageType.MESSAGE_DELIVERED -> {
                                subscribers[message.destination]?.received(message)
                            }

                            MessageType.START_CLASSES_TRANSFER -> {
                                logger.debug { "Starting classes transfer for $agentDebugStr..." }
                            }

                            MessageType.CLASSES_DATA -> {
                                message.bytes.takeIf { it.isNotEmpty() }?.let { rawBytes ->
                                    ProtoBuf.load(ByteArrayListWrapper.serializer(), rawBytes).bytesList.forEach {
                                        adminData.buildManager.addClass(it)
                                    }
                                }
                            }

                            MessageType.FINISH_CLASSES_TRANSFER -> adminData.apply {
                                initClasses()
                                topicResolver.sendToAllSubscribed(WsRoutes.AgentBuilds(agentInfo.id))
                                logger.debug { "Finished classes transfer for $agentDebugStr" }
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
                is io.ktor.utils.io.CancellationException -> logger.error { "Handling of $agentDebugStr was cancelled" }
                else -> logger.error(ex) { "Error handling $agentDebugStr" }
            }
        } finally {
            agentManager.removeInstance(agentInfo.id, instanceId)
        }
    }
}

private fun ApplicationRequest.retrieveParams(): Pair<com.epam.drill.common.AgentConfig, Boolean> {
    val configStr = headers[com.epam.drill.common.AgentConfigParam]!!
    val agentConfig = if (configStr.startsWith('{')) {
        com.epam.drill.common.AgentConfig.serializer() parse configStr
    } else ProtoBuf.loads(com.epam.drill.common.AgentConfig.serializer(), configStr)
    val needSync = headers[com.epam.drill.common.NeedSyncParam]!!.toBoolean()
    return agentConfig to needSync
}
