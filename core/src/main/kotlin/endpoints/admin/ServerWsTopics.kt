package com.epam.drill.admin.endpoints.admin


import com.epam.drill.admin.agent.*
import com.epam.drill.admin.api.routes.*
import com.epam.drill.admin.build.*
import com.epam.drill.admin.common.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.notification.*
import com.epam.drill.admin.plugin.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.admin.router.*
import com.epam.drill.admin.servicegroup.*
import com.epam.drill.admin.version.*
import com.epam.drill.admin.websocket.*
import io.ktor.application.*
import kotlinx.coroutines.*
import org.kodein.di.*
import org.kodein.di.generic.*


class ServerWsTopics(override val kodein: Kodein) : KodeinAware {
    private val wsTopic by instance<WsTopic>()
    private val serviceGroupManager by instance<ServiceGroupManager>()
    private val agentManager by instance<AgentManager>()
    private val plugins by instance<Plugins>()
    private val pluginCaches by instance<PluginCaches>()
    private val app by instance<Application>()
    private val sessionStorage by instance<SessionStorage>()
    private val notificationManager by instance<NotificationManager>()

    init {

        runBlocking {
            agentManager.agentStorage.onUpdate += {
                WsRoot.Agents().send(agentManager.all())
                WsRoot.Groups().send(serviceGroupManager.all())
            }
            agentManager.agentStorage.onAdd += { agentId, agentEntry ->
                WsRoot.Agent(agentId).send(agentEntry.agent.toDto(agentManager))
            }
            agentManager.agentStorage.onRemove += { agentId ->
                agentManager[agentId]?.run {
                    WsRoot.Agent(agentId).send(toDto(agentManager))
                }
            }

            agentManager.agentStorage.onUpdate += {
                val destination = app.toLocation(WsRoutes.Agents())
                val groupedAgents = serviceGroupManager.group(agentManager.activeAgents).toDto(agentManager)
                sessionStorage.sendTo(
                    destination,
                    groupedAgents
                )
                val serviceGroups = groupedAgents.grouped
                if (serviceGroups.any()) {
                    for (group in serviceGroups) {
                        val route = WsRoutes.ServiceGroupPlugins(group.group.id)
                        val dest = app.toLocation(route)
                        sessionStorage.sendTo(dest, group.plugins)
                    }
                }
            }
            agentManager.agentStorage.onAdd += { k, v ->
                val destination = app.toLocation(WsRoutes.Agent(k))
                if (destination in sessionStorage) {
                    sessionStorage.sendTo(
                        destination,
                        v.agent.toDto(agentManager)
                    )
                }
            }

            agentManager.agentStorage.onRemove += { k ->
                val destination = app.toLocation(WsRoutes.Agent(k))
                if (destination in sessionStorage) {
                    sessionStorage.sendTo(destination, "", WsMessageType.DELETE)
                }
            }

            wsTopic {
                topic<WsRoot.Version> { adminVersionDto }

                topic<WsRoot.Agents> { agentManager.all() }

                topic<WsRoot.Agent> { agentManager[it.agentId]?.toDto(agentManager) }

                topic<WsRoot.Groups> { serviceGroupManager.all() }

                topic<WsRoot.Group> { serviceGroupManager[it.groupId] }

                topic<WsRoutes.Agents> {
                    serviceGroupManager.group(agentManager.activeAgents).toDto(agentManager)
                }

                topic<WsRoutes.Agent> { (agentId) ->
                    agentManager.getOrNull(agentId)?.toDto(agentManager)
                }

                topic<WsRoutes.Plugins> { plugins.values.mapToDto() }

                topic<WsRoutes.AgentPlugins> { (agentId) ->
                    agentManager.getOrNull(agentId)?.let { plugins.values.mapToDto(listOf(it)) }
                }

                topic<WsNotifications> {
                    notificationManager.notifications.valuesDesc
                }

                topic<WsRoutes.AgentBuilds> { (agentId) ->
                    agentManager.adminData(agentId).buildManager.agentBuilds.map { agentBuild ->
                        BuildSummaryDto(
                            buildVersion = agentBuild.info.version,
                            detectedAt = agentBuild.detectedAt,
                            summary = pluginCaches.getData(agentId, agentBuild.info.version, type = "build")
                        )
                    }
                }

                topic<WsRoutes.WsVersion> { adminVersionDto }

                topic<WsRoutes.ServiceGroup> { (groupId) -> serviceGroupManager[groupId] }

                topic<WsRoutes.ServiceGroupPlugins> { (groupId) ->
                    val agents = agentManager.activeAgents.filter { it.serviceGroup == groupId }
                    plugins.values.mapToDto(agents)
                }
            }

        }
    }

    private suspend fun Any.send(message: Any) {
        val destination = app.toLocation(this)
        sessionStorage.sendTo(destination, message)
    }
}
