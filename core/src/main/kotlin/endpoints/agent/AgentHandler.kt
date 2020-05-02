@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.epam.drill.admin.endpoints.agent

import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.endpoints.plugin.*
import com.epam.drill.admin.router.*
import com.epam.drill.common.*
import com.epam.drill.admin.common.serialization.*
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.utils.io.*
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
                val (agentConfig, needSync) = call.request.retrieveParams()
                val agentInfo = agentManager.attach(agentConfig, needSync, this)
                createWsLoop(agentInfo, agentConfig.instanceId)
            }
        }
    }

    private fun ApplicationRequest.retrieveParams(): Pair<AgentConfig, Boolean> {
        val agentConfig = ProtoBuf.loads(AgentConfig.serializer(), headers[AgentConfigParam]!!)
        val needSync = headers[NeedSyncParam]!!.toBoolean()
        return agentConfig to needSync
    }

    private suspend fun AgentWsSession.createWsLoop(agentInfo: AgentInfo, instanceId: String) {
        val agentDebugStr = "agent(id=${agentInfo.id}, buildVersion=${agentInfo.buildVersion})"
        try {
            val adminData = agentManager.adminData(agentInfo.id)
            incoming.consumeEach { frame ->
                if (frame is Frame.Binary) {
                    val message = ProtoBuf.load(Message.serializer(), frame.readBytes())
                    logger.trace { "Processing message ${message.type} with data '${message.data}'" }

                    when (message.type) {
                        MessageType.PLUGIN_DATA -> {
                            pd.processPluginData(message.data.decodeToString(), agentInfo)
                        }

                        MessageType.MESSAGE_DELIVERED -> {
                            subscribers[message.destination]?.received(message.data)
                        }

                        MessageType.START_CLASSES_TRANSFER -> {
                            logger.debug { "Starting classes transfer for $agentDebugStr..." }
                            adminData.buildManager.initBuildInfo(agentInfo.buildVersion)
                        }

                        MessageType.CLASSES_DATA -> {
                            ProtoBuf.load(ByteArrayListWrapper.serializer(), message.data).bytesList.forEach {
                                adminData.buildManager.addClass(it)
                            }
                        }

                        MessageType.FINISH_CLASSES_TRANSFER -> {
                            val agentBuild = adminData.buildManager.initClasses(agentInfo.buildVersion)
                            topicResolver.sendToAllSubscribed(WsRoutes.AgentBuilds(agentInfo.id))
                            adminData.store(agentBuild)
                            logger.debug { "Finished classes transfer for $agentDebugStr" }
                        }

                        else -> {
                            logger.warn { "Message with type '${message.type}' is not supported yet" }
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
            agentManager.apply {
                agentInfo.removeInstance(instanceId)
            }
        }
    }
}
