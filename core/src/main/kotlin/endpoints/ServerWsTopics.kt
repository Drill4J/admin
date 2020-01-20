package com.epam.drill.admin.endpoints


import com.epam.drill.admin.agent.*
import com.epam.drill.admin.common.*
import com.epam.drill.admin.servicegroup.*
import com.epam.drill.common.*
import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.admin.router.*
import com.epam.drill.admin.storage.*
import com.epam.drill.admin.util.*
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
            agentManager.agentStorage.onUpdate += update(mutableSetOf()) { storage ->
                val destination = app.toLocation(WsRoutes.GetAllAgents())
                sessionStorage.sendTo(
                    destination,
                    serviceGroupManager.group(storage.values.map { it.agent }.sortedWith(compareBy(AgentInfo::id)))
                        .toDto(agentManager)
                )

            }
            agentManager.agentStorage.onAdd += add(mutableSetOf()) { k, v ->
                val destination = app.toLocation(WsRoutes.GetAgent(k))
                if (sessionStorage.exists(destination)) {
                    sessionStorage.sendTo(
                        destination,
                        v.agent.toAgentInfoWebSocket(agentManager)
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
                    val storage = agentManager.agentStorage
                    serviceGroupManager.group(storage.values.map { it.agent }.sortedWith(compareBy(AgentInfo::id)))
                        .toDto(agentManager)
                }

                topic<WsRoutes.GetAgent> { (agentId) ->
                    agentManager.getOrNull(agentId)?.toAgentInfoWebSocket(agentManager)
                }

                topic<WsRoutes.GetAgentBuilds> { payload ->
                    agentManager.adminData(payload.agentId).buildManager.buildVersionsJson
                }

                topic<WsRoutes.GetAllPlugins> {
                    plugins.map { (_, dp) -> dp.pluginBean }
                        .toAllPluginsWebSocket(agentManager.agentStorage.values.map { it.agent }.toMutableSet())
                }

                topic<WsRoutes.GetPluginInfo> { payload ->
                    val installedPluginBeanIds = agentManager
                        .getAllInstalledPluginBeanIds(payload.agentId)
                    plugins.getAllPluginBeans().map { plug ->
                        val pluginWebSocket = plug.toPluginWebSocket()
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
