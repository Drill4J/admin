package com.epam.drill.endpoints.openapi

import com.epam.drill.common.*
import com.epam.drill.dataclasses.*
import com.epam.drill.endpoints.*
import com.epam.drill.endpoints.agent.*
import com.epam.drill.plugins.*
import com.epam.drill.router.*
import com.epam.drill.util.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.serialization.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*

class DrillAdminEndpoints(override val kodein: Kodein) : KodeinAware {
    private val logger = KotlinLogging.logger {}
    private val app: Application by instance()
    private val agentManager: AgentManager by instance()
    private val plugins: Plugins by kodein.instance()
    private val topicResolver: TopicResolver by instance()
    private val notificationsManager: NotificationsManager by instance()
    private val handler: AdminEndpointsHandler by instance()

    init {
        app.routing {
            authenticate {
                post<Routes.Api.Agent.UnloadPlugin> { (agentId, pluginId) ->
                    logger.debug { "Unload plugin with id $pluginId for agent with id $agentId" }
                    val drillAgent = agentManager.agentSession(agentId)
                    val agentPluginPartFile = plugins[pluginId]?.agentPluginPart

                    val (status, response) = when {
                        drillAgent == null -> {
                            logger.warn { "Drill agent is absent" }
                            HttpStatusCode.NotFound to "Can't find the agent '$agentId'"
                        }
                        agentPluginPartFile == null -> {
                            logger.warn { "Agent plugin part file is absent" }
                            HttpStatusCode.NotFound to "Can't find the plugin '$pluginId' in the agent '$agentId'"
                        }
                        else -> {
                            drillAgent.send(
                                Frame.Text(
                                    Message.serializer() stringify Message(
                                        MessageType.MESSAGE,
                                        "/plugins/unload",
                                        pluginId
                                    )
                                )
                            )
                            logger.info { "Unload plugin with id $pluginId for agent with id $agentId was successfully" }
                            //TODO: implement the agent-side plugin unloading, remove plugin from AgentInfo
                            HttpStatusCode.OK to "Event 'unload' was sent to AGENT"
                        }
                    }
                    call.respondJsonIfErrorsOccured(status, response)
                }

            }

            authenticate {
                post<Routes.Api.Agent.AgentToggleStandby> { (agentId) ->
                    logger.info { "Toggle agent $agentId" }
                    agentManager[agentId]?.let { agentInfo ->
                        agentInfo.status = when (agentInfo.status) {
                            AgentStatus.OFFLINE -> AgentStatus.ONLINE
                            AgentStatus.ONLINE -> AgentStatus.OFFLINE
                            else -> {
                                return@let
                            }
                        }
                        val agentSession = agentManager.agentSession(agentId)
                        agentInfo.plugins.filter { it.enabled }.forEach {
                            agentSession?.send(
                                Frame.Text(
                                    WsSendMessage.serializer() stringify
                                            WsSendMessage(
                                                WsMessageType.MESSAGE,
                                                "/plugins/togglePlugin",
                                                TogglePayload(it.id)
                                            )
                                )
                            )
                        }
                        agentInfo.update(agentManager)
                        logger.info { "Agent $agentId toggled to status ${agentInfo.status.name} successfully" }
                        call.respond(HttpStatusCode.OK, "toggled")
                    }
                }
            }

            authenticate {
                post<Routes.Api.ResetPlugin> { (agentId, pluginId) ->
                    logger.info { "Reset plugin with id $pluginId for agent with id $agentId was successfully" }
                    val agentEntry = agentManager.full(agentId)

                    val (statusCode, response) = when {
                        agentEntry == null -> HttpStatusCode.NotFound to "agent with id $agentId not found"
                        plugins[pluginId] == null -> HttpStatusCode.NotFound to "plugin with id $pluginId not found"
                        else -> {
                            val pluginInstance = agentEntry.instance[pluginId]

                            if (pluginInstance == null) {
                                HttpStatusCode.NotFound to
                                        "plugin with id $pluginId not installed to agent with id $agentId"
                            } else {
                                pluginInstance.dropData()
                                HttpStatusCode.OK to "reset plugin with id $pluginId for agent with id $agentId"
                            }
                        }
                    }
                    logger.info { "Reset plugin with id $pluginId for agent with id $agentId result: $response" }
                    call.respondJsonIfErrorsOccured(statusCode, response)
                }
            }

            authenticate {
                post<Routes.Api.ResetAgent> { (agentId) ->
                    logger.info { "Reset agent with id $agentId" }
                    val agentEntry = agentManager.full(agentId)

                    val (statusCode, response) = when (agentEntry) {
                        null -> HttpStatusCode.NotFound to "agent with id $agentId not found"
                        else -> {
                            agentEntry.instance.values.forEach { pluginInstance -> pluginInstance.dropData() }
                            HttpStatusCode.OK to "reset agent with id $agentId"
                        }
                    }
                    logger.info { "Reset agent with id $agentId result: $response" }
                    call.respondJsonIfErrorsOccured(statusCode, response)
                }
            }

            authenticate {
                post<Routes.Api.ResetAllAgents> {
                    logger.info { "Reset all agents" }
                    agentManager.getAllAgents().forEach { agentEntry ->
                        agentEntry.instance.values.forEach { pluginInstance -> pluginInstance.dropData() }
                    }
                    logger.info { "Reset all agents successfully" }
                    call.respond(HttpStatusCode.OK, "reset drill admin app")
                }
            }

            authenticate {
                post<Routes.Api.ReadNotification> {
                    logger.info { "Read notification" }
                    val callString = call.receiveText()
                    val (statusCode, response) = if (callString.isBlank()) {
                        notificationsManager.readAll()
                        HttpStatusCode.OK to "All notifications successfully read"
                    } else {
                        val (notificationId) = NotificationId.serializer() parse callString
                        if (notificationsManager.read(notificationId)) {
                            topicResolver.sendToAllSubscribed("/notifications")
                            HttpStatusCode.OK to "Notification with id $notificationId successfully read"
                        } else {
                            HttpStatusCode.NotFound to "Notification with id $notificationId not found"
                        }
                    }
                    logger.info { "Notification was reed with result: $response" }
                    call.respondJsonIfErrorsOccured(statusCode, response)
                }
            }

            authenticate {
                post<Routes.Api.DeleteNotification> {
                    val callString = call.receiveText()
                    val (statusCode, response) = if (callString.isBlank()) {
                        notificationsManager.deleteAll()
                        HttpStatusCode.OK to "All notifications successfully deleted"
                    } else {
                        val (notificationId) = NotificationId.serializer() parse callString
                        if (notificationsManager.delete(notificationId)) {
                            topicResolver.sendToAllSubscribed("/notifications")
                            HttpStatusCode.OK to "Notification with id $notificationId successfully deleted"
                        } else {
                            HttpStatusCode.NotFound to "Notification with id $notificationId not found"
                        }
                    }
                    call.respondJsonIfErrorsOccured(statusCode, response)
                }
            }

            authenticate {
                post<Routes.Api.Agent.SystemSettings> { (agentId) ->
                    val systemSettings = SystemSettings.serializer() parse call.receiveText()
                    val adminData = agentManager.adminData(agentId).apply { resetBuilds() }
                    adminData.packagesPrefixes = systemSettings.packagesPrefixes
                    agentManager.applyPackagesChangesOnAllPlugins(agentId)
                    agentManager.wrapBusy(agentManager[agentId]!!) {
                        sessionIdHeaderName = systemSettings.sessionIdHeaderName
                        agentManager.disableAllPlugins(agentId)
                        agentManager.configurePackages(systemSettings.packagesPrefixes, agentId)
                    }
                    adminData.refreshStoredSummary()
                    call.respondJsonIfErrorsOccured(
                        HttpStatusCode.OK,
                        "Trigger for classes processing sent to agent with id $agentId"
                    )
                }
            }

            authenticate {
                post<Routes.Api.Agent.RenameBuildVersion> { (agentId) ->
                    logger.info { "Rename build version" }
                    val buildVersion = AgentBuildVersionJson.serializer() parse call.receiveText()
                    val (statusCode, response) = when (handler.updateBuildAliasWithResult(
                        agentId,
                        buildVersion
                    )) {
                        HttpStatusCode.BadRequest -> HttpStatusCode.BadRequest to "Build named ${buildVersion.name} already exists"
                        HttpStatusCode.OK -> HttpStatusCode.OK to "Agent with id $agentId have been updated"
                        else -> HttpStatusCode.InternalServerError to "Request handle with exception"
                    }
                    logger.info { "Build version rename was finished with result: $response" }
                    call.respondJsonIfErrorsOccured(statusCode, response)
                }
            }
        }
    }
}

@Serializable
data class SystemSettings(
    val packagesPrefixes: List<String> = emptyList(),
    val sessionIdHeaderName: String = ""
)