@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.epam.drill.admin.endpoints.agent

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.config.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.endpoints.plugin.*
import com.epam.drill.admin.router.*
import com.epam.drill.admin.util.*
import com.epam.drill.common.*
import io.ktor.application.*
import io.ktor.http.HttpHeaders.ContentEncoding
import io.ktor.http.cio.websocket.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*

private val logger = KotlinLogging.logger {}

class AgentHandler(override val kodein: Kodein) : KodeinAware {
    private val app by instance<Application>()
    private val agentManager by instance<AgentManager>()
    private val pd by instance<PluginDispatcher>()
    private val topicResolver by instance<TopicResolver>()

    init {
        app.routing {
            agentWebsocket("/agent/attach") {
                withContext(Dispatchers.IO) {
                    val (agentConfig, needSync) = call.request.retrieveParams()
                    val frameType = when (agentConfig.agentType) {
                        AgentType.NODEJS, AgentType.DOTNET -> FrameType.TEXT
                        else -> FrameType.BINARY
                    }
                    val agentSession = AgentWsSession(this@agentWebsocket, frameType, application.agentSocketTimeout)
                    val agentInfo = agentManager.attach(agentConfig, needSync, agentSession)
                    agentSession.createWsLoop(
                        agentInfo,
                        agentConfig.instanceId,
                        call.request.headers[ContentEncoding] == "deflate"
                    )
                }
            }
        }
    }

    private fun ApplicationRequest.retrieveParams(): Pair<AgentConfig, Boolean> {
        val configStr = headers[AgentConfigParam]!!
        val agentConfig = if (configStr.startsWith('{')) {
            AgentConfig.serializer() parse configStr
        } else ProtoBuf.loads(AgentConfig.serializer(), configStr)
        val needSync = headers[NeedSyncParam]!!.toBoolean()
        return agentConfig to needSync
    }

    private suspend fun AgentWsSession.createWsLoop(agentInfo: AgentInfo, instanceId: String, useCompression: Boolean) {
        val agentDebugStr = "agent(id=${agentInfo.id}, buildVersion=${agentInfo.buildVersion})"
        try {
            val adminData = agentManager.adminData(agentInfo.id)
            incoming.consumeEach { frame ->
                when (frame) {
                    is Frame.Binary -> {
                        withContext(Dispatchers.IO) {
                            val rawContent = frame.readBytes()
                            val frameBytes = if (useCompression) {
                                Deflate.decode(rawContent)
                            } else rawContent
                            BinaryMessage(ProtoBuf.load(Message.serializer(), frameBytes))
                        }
                    }
                    is Frame.Text -> JsonMessage.serializer() parse frame.readText()
                    else -> null
                }?.let { message ->
                    logger.trace { "Processing message $message." }

                    when (message.type) {
                        MessageType.PLUGIN_DATA -> {
                            withContext(Dispatchers.IO) {
                                pd.processPluginData(message.text, agentInfo)
                            }
                        }

                        MessageType.MESSAGE_DELIVERED -> {
                            subscribers[message.destination]?.received(message)
                        }

                        MessageType.START_CLASSES_TRANSFER -> {
                            logger.debug { "Starting classes transfer for $agentDebugStr..." }
                        }

                        MessageType.CLASSES_DATA -> {
                            withContext(Dispatchers.IO) {
                                ProtoBuf.load(ByteArrayListWrapper.serializer(), message.bytes).bytesList.forEach {
                                    adminData.buildManager.addClass(it)
                                }
                            }
                        }

                        MessageType.FINISH_CLASSES_TRANSFER -> {
                            withContext(Dispatchers.IO) {
                                val agentBuild = adminData.buildManager.initClasses(agentInfo.buildVersion)
                                topicResolver.sendToAllSubscribed(WsRoutes.AgentBuilds(agentInfo.id))
                                adminData.store(agentBuild)
                                logger.debug { "Finished classes transfer for $agentDebugStr" }
                            }
                        }

                        else -> {
                            logger.warn { "Message with type '${message.type}' is not supported yet" }
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            when (ex) {
                is io.ktor.utils.io.CancellationException  -> logger.error { "Handling of $agentDebugStr was cancelled" }
                else -> logger.error(ex) { "Error handling $agentDebugStr" }
            }
        } finally {
            agentManager.apply {
                agentInfo.removeInstance(instanceId)
            }
        }
    }
}
