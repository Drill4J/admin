@file:Suppress("EXPERIMENTAL_API_USAGE", "UNCHECKED_CAST")

package com.epam.drill.endpoints.agent

import com.epam.drill.common.*
import com.epam.drill.common.ws.*
import com.epam.drill.endpoints.*
import com.epam.drill.endpoints.plugin.*
import com.epam.drill.system.*
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.websocket.*
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
                val (agentConfig, needSync) = retrieveParams()
                val agentInfo = agentManager.agentConfiguration(agentConfig)
                val sslPort = app.securePort()

                agentInfo.ipAddress = call.request.local.remoteHost
                agentManager.put(agentInfo, this)
                agentManager.update()
                agentManager.adminData(agentInfo.id).loadStoredData()
                agentManager.sync(agentInfo, needSync)

                logger.info {
                    "Agent WS is connected." +
                            " Client's address is ${call.request.local.remoteHost}," +
                            " ssl port is '$sslPort and needSync is $needSync"
                }

                sendToTopic("/agent/config", ServiceConfig(sslPort, agentInfo.sessionIdHeaderName)).call()
                createWsLoop(agentInfo, agentConfig.instanceId)
            }
        }
    }


    private fun DefaultWebSocketServerSession.retrieveParams(): Pair<AgentConfig, Boolean> {
        val agentConfig = Cbor.loads(AgentConfig.serializer(), call.request.headers[AgentConfigParam]!!)
        val needSync = call.request.headers[NeedSyncParam]!!.toBoolean()
        return agentConfig to needSync
    }

    private suspend fun AgentWsSession.createWsLoop(agentInfo: AgentInfo, instanceId: String) {

        try {
            incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    val message = Message.serializer() parse frame.readText()
                    logger.debug { "Processing message ${message.type} with data '${message.data}'" }

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
                            logger.debug { "Starting classes transfer" }
                            agentManager.adminData(agentInfo.id).run {
                                buildManager.setupBuildInfo(agentInfo.buildVersion, agentInfo.buildAlias)
                                refreshStoredSummary()
                            }
                        }

                        MessageType.CLASSES_DATA -> {
                            agentManager.adminData(agentInfo.id)
                                .buildManager
                                .addClass(agentInfo.buildVersion, message.data)
                        }

                        MessageType.FINISH_CLASSES_TRANSFER -> {
                            agentManager.adminData(agentInfo.id)
                                .buildManager
                                .compareToPrev(agentInfo.buildVersion)
                            agentManager.applyPackagesChangesOnAllPlugins(agentInfo.id)
                            topicResolver.sendToAllSubscribed("/${agentInfo.id}/builds")
                            agentManager.enableAllPlugins(agentInfo.id)
                            logger.debug { "Finished classes transfer" }
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
            agentInfo.instanceIds.remove(instanceId)
            if (agentInfo.instanceIds.isEmpty()) {
                agentManager.remove(agentInfo)
                logger.info { "Agent with id '${agentInfo.id}' was disconnected" }
            } else{
                agentManager.singleUpdate(agentInfo.id)
                logger.info { "Instance '$instanceId' of Agent '${agentInfo.id}' was disconnected" }
            }
        }
    }
}
