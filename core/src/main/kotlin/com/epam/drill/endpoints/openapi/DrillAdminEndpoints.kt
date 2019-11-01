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
import io.ktor.locations.post
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import org.kodein.di.*
import org.kodein.di.generic.*

class DrillAdminEndpoints(override val kodein: Kodein) : KodeinAware {
    private val app: Application by instance()
    private val agentManager: AgentManager by instance()
    private val plugins: Plugins by kodein.instance()
    private val topicResolver: TopicResolver by instance()
    private val notificationsManager: NotificationsManager by instance()

    init {
        app.routing {
            authenticate {
                post<Routes.Api.Agent.UnloadPlugin> { (agentId, pluginId) ->
                    val drillAgent = agentManager.agentSession(agentId)
                    if (drillAgent == null) {
                        call.respond("can't find the agent '$agentId'")
                        return@post
                    }
                    val agentPluginPartFile = plugins[pluginId]?.agentPluginPart
                    if (agentPluginPartFile == null) {
                        call.respond("can't find the plugin '$pluginId' in the agent '$agentId'")
                        return@post
                    }
                    drillAgent.send(
                        Frame.Text(
                            Message.serializer() stringify Message(
                                MessageType.MESSAGE,
                                "/plugins/unload",
                                pluginId
                            )
                        )
                    )
                    //TODO: implement the agent-side plugin unloading, remove plugin from AgentInfo
                    call.respond("event 'unload' was sent to AGENT")
                }
            }

            authenticate {
                post<Routes.Api.Agent.AgentToggleStandby> { (agentId) ->
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
                                            WsSendMessage(WsMessageType.MESSAGE, "/plugins/togglePlugin", it.id)
                                )
                            )
                        }
                        agentInfo.update(agentManager)
                        call.respond(HttpStatusCode.OK, "toggled")
                    }
                }
            }

            authenticate {
                post<Routes.Api.ResetPlugin> { (agentId, pluginId) ->
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
                    call.respond(statusCode, response)
                }
            }

            authenticate {
                post<Routes.Api.ResetAgent> { (agentId) ->
                    val agentEntry = agentManager.full(agentId)
                    val (statusCode, response) = when {
                        agentEntry == null -> HttpStatusCode.NotFound to "agent with id $agentId not found"
                        else -> {
                            agentEntry.instance.values.forEach { pluginInstance -> pluginInstance.dropData() }
                            HttpStatusCode.OK to "reset agent with id $agentId"
                        }
                    }
                    call.respond(statusCode, response)
                }
            }

            authenticate {
                post<Routes.Api.ResetAllAgents> {
                    agentManager.getAllAgents().forEach { agentEntry ->
                        agentEntry.instance.values.forEach { pluginInstance -> pluginInstance.dropData() }
                    }
                    call.respond(HttpStatusCode.OK, "reset drill admin app")
                }
            }

            authenticate {
                post<Routes.Api.ReadNotification> {
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
                    call.respond(statusCode, response)
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
                    call.respond(statusCode, response)
                }
            }

            authenticate {
                post<Routes.Api.Agent.SetPackages> { (agentId) ->
                    val prefixes = PackagesPrefixes.serializer() parse call.receiveText()
                    agentManager.configurePackages(prefixes, agentId)
                    agentManager.adminData(agentId).packagesPrefixes = prefixes
                    call.respond(HttpStatusCode.OK, "Trigger for classes processing sent to agent with id $agentId")
                }
            }

            authenticate {
                post<Routes.Api.Agent.RenameBuildVersion> { (agentId) ->
                    val buildVersion = AgentBuildVersionJson.serializer() parse call.receiveText()
                    val agentInfo = agentManager.updateAgentBuildAliases(agentId, buildVersion)
                    val (statusCode, response) = if (agentInfo == null) {
                        HttpStatusCode.NotFound to "Agent with id $agentId not found"
                    } else {
                        HttpStatusCode.OK to "Agent with id $agentId have been updated"
                    }
                    call.respond(statusCode, response)
                }
            }
        }
    }
}
