package com.epam.drill.admin.endpoints.openapi

import com.epam.drill.admin.agent.*
import com.epam.drill.api.*
import com.epam.drill.common.*
import com.epam.drill.admin.dataclasses.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.admin.router.*
import com.epam.drill.admin.util.*
import de.nielsfalk.ktor.swagger.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.response.*
import io.ktor.routing.*
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
                val unloadPluginResponds = "Unload plugin"
                    .responds(
                        ok<String>(
                            example("result", "Unload plugin with such id for agent with such id was successfully")
                        ), notFound(), badRequest()
                    )
                post<Routes.Api.Agent.UnloadPlugin>(unloadPluginResponds) { payload ->
                    val (agentId, pluginId) = payload
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
                val agentToggleStandByResponds = "Agent Toggle StandBy"
                    .responds(
                        ok<String>(
                            example("result", "toggled")
                        )
                    )
                post<Routes.Api.Agent.AgentToggleStandby>(agentToggleStandByResponds) { params ->
                    val (agentId) = params
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
                            agentSession?.sendToTopic<Communication.Plugin.ToggleEvent>(TogglePayload(it.id))
                        }
                        with(agentManager) { agentInfo.commitChanges() }
                        logger.info { "Agent $agentId toggled to status ${agentInfo.status.name} successfully" }
                        call.respond(HttpStatusCode.OK, "toggled")
                    }
                }
            }

            authenticate {
                val resetPluginResponds = "Reset plugin"
                    .responds(
                        ok<String>(
                            example("result", "reset plugin with such id for agent with such id")
                        ), notFound(), badRequest()
                    )
                post<Routes.Api.ResetPlugin>(resetPluginResponds) { payload ->
                    val (agentId, pluginId) = payload
                    logger.info { "Reset plugin with id $pluginId for agent with id $agentId was successfully" }
                    val agentEntry = agentManager.full(agentId)

                    val (statusCode, response) = when {
                        agentEntry == null -> HttpStatusCode.NotFound to "agent with id $agentId not found"
                        plugins[pluginId] == null -> HttpStatusCode.NotFound to "plugin with id $pluginId not found"
                        else -> {
                            val pluginInstance = agentEntry[pluginId]

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
                val resetAgentResponse = "Reset agent"
                    .responds(
                        ok<String>(
                            example("result", "Reset agent with such id")
                        ), notFound()
                    )
                post<Routes.Api.ResetAgent>(resetAgentResponse) { payload ->
                    val (agentId) = payload
                    logger.info { "Reset agent with id $agentId" }
                    val agentEntry = agentManager.full(agentId)

                    val (statusCode, response) = when (agentEntry) {
                        null -> HttpStatusCode.NotFound to "agent with id $agentId not found"
                        else -> {
                            agentEntry.plugins.forEach { pluginInstance -> pluginInstance.dropData() }
                            HttpStatusCode.OK to "reset agent with id $agentId"
                        }
                    }
                    logger.info { "Reset agent with id $agentId result: $response" }
                    call.respondJsonIfErrorsOccured(statusCode, response)
                }
            }

            authenticate {
                val resetAllAgentsResponds = "Reset all agents"
                    .responds(
                        ok<String>(
                            example("result", "reset drill admin app")
                        )
                    )
                post<Routes.Api.ResetAllAgents>(resetAllAgentsResponds) { _ ->
                    logger.info { "Reset all agents" }
                    agentManager.getAllAgents().forEach { agentEntry ->
                        agentEntry.plugins.forEach { pluginInstance -> pluginInstance.dropData() }
                    }
                    logger.info { "Reset all agents successfully" }
                    call.respond(HttpStatusCode.OK, "reset drill admin app")
                }
            }

            authenticate {
                val notificationResponds = "Read Notification"
                    .examples(
                        example("notification", NotificationId("Some notification id"))
                    )
                    .responds(
                        ok<String>(
                            example("result", "All notifications successfully read"),
                            example("result", "Notification with such id successfully read")
                        ),
                        notFound()
                    )
                post<Routes.Api.ReadNotification, NotificationId>(notificationResponds) { _, notificationIdObject ->
                    logger.info { "Read notification" }
                    val (statusCode, response) = if (notificationIdObject.notificationId.isBlank()) {
                        notificationsManager.readAll()
                        HttpStatusCode.OK to "All notifications successfully read"
                    } else {
                        val (notificationId) = notificationIdObject
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
                val notificationResponds = "Delete Notification"
                    .examples(
                        example("notification", NotificationId("Some notification id"))
                    )
                    .responds(
                        ok<String>(
                            example("result", "All notifications successfully deleted")
                        ),
                        notFound()
                    )
                post<Routes.Api.DeleteNotification, NotificationId>(notificationResponds) { _, notificationIdObject ->
                    val (statusCode, response) = if (notificationIdObject.notificationId.isBlank()) {
                        notificationsManager.deleteAll()
                        HttpStatusCode.OK to "All notifications successfully deleted"
                    } else {
                        val (notificationId) = notificationIdObject
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
                val systemSettingsResponds = "Update system settings"
                    .examples(
                        example(
                            "systemSettings",
                            SystemSettingsDto(listOf("some package prefixes"), "some session header name")
                        )
                    )
                    .responds(
                        ok<String>(
                            example("Trigger for classes processing sent to agent with such id")
                        ),
                        badRequest()
                    )
                post<Routes.Api.Agent.SystemSettings, SystemSettingsDto>(systemSettingsResponds) { params, systemSettings ->
                    val (agentId) = params
                    val statusCode = handler.updateSystemSettings(agentId, systemSettings)

                    val response = when (statusCode) {
                        HttpStatusCode.OK -> "Trigger for classes processing sent to agent with id $agentId"
                        HttpStatusCode.BadRequest -> "Package prefixes contains empty value"
                        else -> "Request handle with exception"
                    }
                    logger.info { "System settings update was finished with result: $response" }
                    call.respondJsonIfErrorsOccured(statusCode, response)
                }
            }

            authenticate {
                val renameBuildVersionResponds = "Rename build version"
                    .examples(
                        example(
                            "agent build version name", AgentBuildVersionJson("some build id", "some build version name")
                        )
                    )
                    .responds(
                        ok<String>(
                            example("result", "Agent with such id have been updated")
                        ),
                        internalServerError<String>(
                            example("error", "Request handle with exception")
                        )
                    )
                post<Routes.Api.Agent.RenameBuildVersion, AgentBuildVersionJson>(renameBuildVersionResponds) { payload, buildVersion ->
                    logger.info { "Rename build version" }
                    val (agentId) = payload
                    val (statusCode, response) = when (handler.updateBuildAliasWithResult(
                        agentId,
                        buildVersion
                    )) {
                        HttpStatusCode.NotFound -> HttpStatusCode.BadRequest to "Build with id ${buildVersion.id} does not exist"
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
