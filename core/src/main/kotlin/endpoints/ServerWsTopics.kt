package com.epam.drill.admin.endpoints


import com.epam.drill.admin.agent.*
import com.epam.drill.admin.common.*
import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.admin.notification.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.admin.router.*
import com.epam.drill.admin.servicegroup.*
import com.epam.drill.admin.storage.*
import com.epam.drill.admin.version.*
import com.epam.drill.common.*
import io.ktor.application.*
import kotlinx.coroutines.*
import org.kodein.di.*
import org.kodein.di.generic.*


class ServerWsTopics(override val kodein: Kodein) : KodeinAware {
    private val wsTopic by instance<WsTopic>()
    private val serviceGroupManager by instance<ServiceGroupManager>()
    private val agentManager by instance<AgentManager>()
    private val plugins by instance<Plugins>()
    private val app by instance<Application>()
    private val sessionStorage by instance<SessionStorage>()
    private val notificationManager by instance<NotificationManager>()

    init {

        runBlocking {
            agentManager.agentStorage.onUpdate += update(mutableSetOf()) {
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
            agentManager.agentStorage.onAdd += add(mutableSetOf()) { k, v ->
                val destination = app.toLocation(WsRoutes.Agent(k))
                if (destination in sessionStorage) {
                    sessionStorage.sendTo(
                        destination,
                        v.agent.toDto(agentManager)
                    )

                }
            }

            agentManager.agentStorage.onRemove += remove(mutableSetOf()) { k ->
                val destination = app.toLocation(WsRoutes.Agent(k))
                if (destination in sessionStorage) {
                    sessionStorage.sendTo(destination, "", WsMessageType.DELETE)
                }
            }

            wsTopic {
                topic<WsRoutes.Agents> {
                    serviceGroupManager.group(agentManager.activeAgents).toDto(agentManager)
                }

                topic<WsRoutes.Agent> { (agentId) ->
                    agentManager.getOrNull(agentId)?.toDto(agentManager)
                }

                topic<WsRoutes.Plugins> {
                    plugins.values.mapToDto(agentManager.agentStorage.values.map { it.agent })
                }

                topic<WsRoutes.AgentPlugins> { payload ->
                    agentManager.getAllInstalledPluginBeanIds(payload.agentId)?.let { ids ->
                        plugins.values.mapToDto().map { plugin ->
                            if (plugin.id in ids) {
                                plugin.copy(relation = "Installed")
                            } else plugin
                        }
                    }
                }

                topic<WsRoutes.AgentPluginConfig> { payload ->
                    agentManager.getOrNull(payload.agent)?.plugins?.find { it.id == payload.plugin }
                        ?.config?.let { json.parseJson(it) } ?: ""
                }

                topic<WsNotifications> {
                    notificationManager.notifications.valuesDesc
                }

                topic<WsRoutes.AgentBuilds> { (agentId) ->
                    agentManager.adminData(agentId).buildManager.dtoList()
                }

                topic<WsRoutes.WsVersion> { adminVersionDto }

                topic<WsRoutes.ServiceGroup> { (groupId) -> serviceGroupManager[groupId] }

                topic<WsRoutes.ServiceGroupPlugins> { (groupId) ->
                    agentManager.run {
                        val agents = activeAgents.filter { it.serviceGroup == groupId }
                        plugins.values.ofAgents(agents).mapToDto()
                    }
                }
            }

        }
    }
}
