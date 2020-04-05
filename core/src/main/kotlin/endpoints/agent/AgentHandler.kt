@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.epam.drill.admin.endpoints.agent

import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.endpoints.plugin.*
import com.epam.drill.admin.router.*
import com.epam.drill.common.*
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.request.*
import io.ktor.routing.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.*
import kotlinx.serialization.cbor.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*
import java.util.concurrent.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

private val logger = KotlinLogging.logger {}

class AgentHandler(override val kodein: Kodein) : KodeinAware {
    private val app: Application by instance()
    private val agentManager: AgentManager by instance()
    private val pd: PluginDispatcher by kodein.instance()
    private val topicResolver: TopicResolver by instance()

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
        val agentConfig = Cbor.loads(AgentConfig.serializer(), headers[AgentConfigParam]!!)
        val needSync = headers[NeedSyncParam]!!.toBoolean()
        return agentConfig to needSync
    }

    private suspend fun AgentWsSession.createWsLoop(agentInfo: AgentInfo, instanceId: String) {

        try {
            val adminData = agentManager.adminData(agentInfo.id)
            val agentDebugStr = "agent(id=${agentInfo.id}, buildVersion=${agentInfo.buildVersion})"
            incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    val message = Message.serializer() parse frame.readText()
                    logger.trace { "Processing message ${message.type} with data '${message.data}'" }

                    when (message.type) {
                        MessageType.PLUGIN_DATA -> {
                            pd.processPluginData(message.data, agentInfo)
                        }

                        MessageType.MESSAGE_DELIVERED -> {
                            subscribers[message.destination]?.apply {
                                if (callback.reflect()?.parameters?.get(0)?.type == Unit::class.starProjectedType)
                                    (callback as suspend (Unit) -> Unit).invoke(Unit)
                                else
                                    callback.invoke(message.data)
                                state = false
                            }
                        }

                        MessageType.START_CLASSES_TRANSFER -> {
                            logger.debug { "Starting classes transfer for $agentDebugStr..." }
                            adminData.buildManager.initBuildInfo(agentInfo.buildVersion)
                        }

                        MessageType.CLASSES_DATA -> {
                            adminData.buildManager.addClass(message.data)
                        }

                        MessageType.FINISH_CLASSES_TRANSFER -> {
                            val agentBuild = adminData.buildManager.initClasses(agentInfo.buildVersion)
                            agentManager.enableAllPlugins(agentInfo.id)
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
                is CancellationException -> logger.error { "Handle the agent was cancelled" }
                else -> logger.error(ex) { "Handle with exception" }
            }
        } finally {
            agentManager.apply {
                agentInfo.removeInstance(instanceId)
            }
        }
    }
}
