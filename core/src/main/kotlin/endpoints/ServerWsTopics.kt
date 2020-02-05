package com.epam.drill.admin.endpoints


import com.epam.drill.admin.agent.*
import com.epam.drill.admin.common.*
import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.admin.router.*
import com.epam.drill.admin.servicegroup.*
import com.epam.drill.admin.storage.*
import com.epam.drill.admin.util.*
import com.epam.drill.common.*
import io.ktor.application.*
import kotlinx.coroutines.*
import org.kodein.di.*
import org.kodein.di.generic.*


class ServerWsTopics(override val kodein: Kodein) : KodeinAware {
    private val wsTopic: WsTopic by instance()
    private val serviceGroupManager: ServiceGroupManager by instance()
    private val agentManager: AgentManager by instance()
    private val plugins: Plugins by instance()
    private val app: Application by instance()
    private val sessionStorage: SessionStorage by instance()
    private val notificationsManager: NotificationsManager by instance()

    init {

        runBlocking {
            agentManager.agentStorage.onUpdate += update(mutableSetOf()) {
                val destination = app.toLocation(WsRoutes.GetAllAgents())
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
                val destination = app.toLocation(WsRoutes.GetAgent(k))
                if (sessionStorage.exists(destination)) {
                    sessionStorage.sendTo(
                        destination,
                        v.agent.toDto(agentManager)
                    )

                }
            }

            agentManager.agentStorage.onRemove += remove(mutableSetOf()) { k ->
                val destination = app.toLocation(WsRoutes.GetAgent(k))
                if (sessionStorage.exists(destination))
                    sessionStorage.sendTo(destination, "", WsMessageType.DELETE)
            }

            wsTopic {
                topic<WsRoutes.GetAllAgents> {
                    serviceGroupManager.group(agentManager.activeAgents).toDto(agentManager)
                }

                topic<WsRoutes.GetAgent> { (agentId) ->
                    agentManager.getOrNull(agentId)?.toDto(agentManager)
                }

                topic<WsRoutes.GetAgentBuilds> { payload ->
                    agentManager.adminData(payload.agentId).buildManager.buildVersionsJson
                }

                topic<WsRoutes.GetAllPlugins> {
                    plugins.map { (_, dp) -> dp.pluginBean }
                        .mapToDto(agentManager.agentStorage.values.map { it.agent })
                }

                topic<WsRoutes.GetPluginInfo> { payload ->
                    val installedPluginBeanIds = agentManager
                        .getAllInstalledPluginBeanIds(payload.agentId)
                    plugins.getAllPluginBeans().map { plug ->
                        val pluginWebSocket = plug.toDto()
                        if (plug partOf installedPluginBeanIds) {
                            pluginWebSocket.relation = "Installed"
                        }
                        pluginWebSocket
                    }
                }

                topic<WsRoutes.GetPluginConfig> { payload ->
                    agentManager.getOrNull(payload.agent)?.plugins?.find { it.id == payload.plugin }
                        ?.config?.let { json.parseJson(it) } ?: ""
                }

                topic<WsRoutes.GetNotifications> {
                    notificationsManager.allNotifications.sortedByDescending { it.date }
                }

                topic<WsRoutes.GetBuilds> { (agentId) ->
                    agentManager.adminData(agentId).buildManager.summaries.sortedByDescending { it.addedDate }
                }
            }

        }
    }
}
