package com.epam.drill.admin.endpoints.openapi

import com.epam.drill.admin.agent.*
import com.epam.drill.api.*
import com.epam.drill.common.*
import de.nielsfalk.ktor.swagger.*
import com.epam.drill.admin.dataclasses.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.admin.router.*
import com.epam.drill.admin.util.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.utils.*
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
                        ok<Unit>(), notFound(), badRequest()
                    )
                delete<Routes.Api.Agents.Plugin>(unloadPluginResponds) { payload ->
                    val (agentId, pluginId) = payload
                    logger.debug { "Unload plugin with id $pluginId for agent with id $agentId" }
                    val drillAgent = agentManager.agentSession(agentId)
                    val agentPluginPartFile = plugins[pluginId]?.agentPluginPart

                    val (status, response) = when {
                        drillAgent == null -> {
                            logger.warn { "Drill agent is absent" }
                            HttpStatusCode.NotFound to ErrorResponse("Can't find the agent '$agentId'")
                        }
                        agentPluginPartFile == null -> {
                            logger.warn { "Agent plugin part file is absent" }
                            HttpStatusCode.NotFound to ErrorResponse("Can't find the plugin '$pluginId' in the agent '$agentId'")
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
                            HttpStatusCode.OK to EmptyContent
                        }
                    }
                    call.respond(status, response)
                }

            }

            authenticate {
                val agentToggleStandByResponds = "Agent Toggle StandBy"
                    .responds(
                        ok<Unit>(), notFound(), badRequest()
                    )
                post<Routes.Api.Agents.ToggleAgent>(agentToggleStandByResponds) { params ->
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
                        call.respond(HttpStatusCode.OK, EmptyContent)
                    }
                }
            }

            authenticate {
                val resetPluginResponds = "Reset plugin"
                    .responds(
                        ok<Unit>(), notFound(), badRequest()
                    )
                post<Routes.Api.Agents.ResetPlugin>(resetPluginResponds) { payload ->
                    val (agentId, pluginId) = payload
                    logger.info { "Reset plugin with id $pluginId for agent with id $agentId was successfully" }
                    val agentEntry = agentManager.full(agentId)

                    val (statusCode, response) = when {
                        agentEntry == null -> HttpStatusCode.NotFound to ErrorResponse("agent with id $agentId not found")
                        plugins[pluginId] == null -> HttpStatusCode.NotFound to ErrorResponse("plugin with id $pluginId not found")
                        else -> {
                            val pluginInstance = agentEntry[pluginId]

                            if (pluginInstance == null) {
                                HttpStatusCode.NotFound to
                                        ErrorResponse("plugin with id $pluginId not installed to agent with id $agentId")
                            } else {
                                pluginInstance.dropData()
                                HttpStatusCode.OK to EmptyContent
                            }
                        }
                    }
                    logger.info { "Reset plugin with id $pluginId for agent with id $agentId result: $response" }
                    call.respond(statusCode, response)
                }
            }

            authenticate {
                val resetAgentResponse = "Reset agent"
                    .responds(
                        ok<Unit>(), notFound()
                    )
                put<Routes.Api.Agents.ResetAgent, Unit>(resetAgentResponse) { payload, _ ->
                    val (agentId) = payload
                    logger.info { "Reset agent with id $agentId" }
                    val agentEntry = agentManager.full(agentId)

                    val (statusCode, response) = when (agentEntry) {
                        null -> HttpStatusCode.NotFound to ErrorResponse("agent with id $agentId not found")
                        else -> {
                            agentEntry.plugins.forEach { pluginInstance -> pluginInstance.dropData() }
                            HttpStatusCode.OK to EmptyContent
                        }
                    }
                    logger.info { "Reset agent with id $agentId result: $response" }
                    call.respond(statusCode, response)
                }
            }

            authenticate {
                val resetAllAgentsResponds = "Reset all agents"
                    .responds(
                        ok<Unit>()
                    )
                put<Routes.Api.Agents.Reset, Unit>(resetAllAgentsResponds) { _, _ ->
                    logger.info { "Reset all agents" }
                    agentManager.getAllAgents().forEach { agentEntry ->
                        agentEntry.plugins.forEach { pluginInstance -> pluginInstance.dropData() }
                    }
                    logger.info { "Reset all agents successfully" }
                    call.respond(HttpStatusCode.OK, EmptyContent)
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
                            HttpStatusCode.NotFound to ErrorResponse("Notification with id $notificationId not found")
                        }
                    }
                    logger.info { "Notification was reed with result: $response" }
                    call.respond(statusCode, response)
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
                            HttpStatusCode.NotFound to ErrorResponse("Notification with id $notificationId not found")
                        }
                    }
                    call.respond(statusCode, response)
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
                        ok<String>(),
                        badRequest()
                    )
                put<Routes.Api.Agents.SystemSettings, SystemSettingsDto>(systemSettingsResponds) { params, systemSettings ->
                    val (agentId) = params
                    val statusCode = handler.updateSystemSettings(agentId, systemSettings)

                    val response: Any = when (statusCode) {
                        HttpStatusCode.OK -> EmptyContent
                        HttpStatusCode.BadRequest -> ErrorResponse("Package prefixes contains empty value")
                        else -> ErrorResponse("Request handle with exception")
                    }
                    logger.info { "System settings update was finished with result: $response" }
                    call.respond(statusCode, response)
                }
            }

        }
    }
}
