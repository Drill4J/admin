/**
 * Copyright 2020 - 2022 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.drill.admin.endpoints.admin


import com.epam.drill.admin.agent.*
import com.epam.drill.admin.api.routes.*
import com.epam.drill.admin.build.*
import com.epam.drill.admin.common.*
import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.group.*
import com.epam.drill.admin.notification.*
import com.epam.drill.admin.plugin.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.admin.router.*
import com.epam.drill.admin.version.*
import com.epam.drill.admin.websocket.*
import io.ktor.application.*
import kotlinx.coroutines.*
import org.kodein.di.*


class ServerWsTopics(override val di: DI) : DIAware {
    private val wsTopic by instance<WsTopic>()
    private val groupManager by instance<GroupManager>()
    private val agentManager by instance<AgentManager>()
    private val buildManager by instance<BuildManager>()
    private val plugins by instance<Plugins>()
    private val pluginCaches by instance<PluginCaches>()
    private val app by instance<Application>()
    private val sessionStorage by instance<SessionStorage>()
    private val notificationManager by instance<NotificationManager>()

    init {

        runBlocking {
            agentManager.agentStorage.onUpdate += {
                WsRoot.Agents().send(agentManager.all())
                WsRoot.Groups().send(groupManager.all())
                //TODO remove after EPMDJ-8323
                WsRoot.AgentsActiveBuild().send(agentManager.agentsActiveBuild(buildManager))
            }
            agentManager.agentStorage.onAdd += { agentId, agent ->
                WsRoot.Agent(agentId).send(agent.info.toDto(agentManager))
            }
            agentManager.agentStorage.onRemove += { agentId ->
                agentManager[agentId]?.run {
                    WsRoot.Agent(agentId).send(toDto(agentManager))
                }
            }

            //TODO EPMDJ-8454 should send list, after multiple builds will be implemented
            buildManager.buildStorage.onUpdate += { builds ->
                builds.keys.forEach {
                    agentManager[it.agentId]?.toAgentBuildDto(buildManager)?.let { agentBuildInfoDto ->
                        WsRoot.AgentBuild(it.agentId, it.buildVersion).send(agentBuildInfoDto)
                        WsRoot.AgentBuilds(it.agentId).send(listOf(agentBuildInfoDto))
                    }
                }

            }

            buildManager.buildStorage.onAdd += { agentBuildKey, _ ->
                agentManager[agentBuildKey.agentId]?.toAgentBuildDto(buildManager)?.let { buildDto ->
                    WsRoot.AgentBuild(agentBuildKey.agentId, agentBuildKey.buildVersion).send(buildDto)
                    WsRoot.AgentBuilds(agentBuildKey.agentId).send(listOf(buildDto))
                }
            }

            agentManager.agentStorage.onUpdate += {
                val groupedAgents = groupManager.group(agentManager.activeAgents).toDto(agentManager)
                WsRoutes.Agents().send(groupedAgents)
                val groups = groupedAgents.grouped
                if (groups.any()) {
                    for (group in groups) {
                        val route = WsRoutes.GroupPlugins(group.group.id)
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
                        v.info.toDto(agentManager)
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

                topic<WsRoot.AgentsActiveBuild> { agentManager.agentsActiveBuild(buildManager) }

                topic<WsRoot.Agent> { agentManager[it.agentId]?.toDto(agentManager) }

                //TODO EPMDJ-8454 should send list, after multiple builds will be implemented
                topic<WsRoot.AgentBuilds> {
                    agentManager[it.agentId]?.toAgentBuildDto(buildManager)?.let { listOf(it) }
                }

                topic<WsRoot.AgentBuild> { agentManager[it.agentId]?.toAgentBuildDto(buildManager) }

                topic<WsRoot.Groups> { groupManager.all() }

                topic<WsRoot.Group> { groupManager[it.groupId] }

                topic<WsRoutes.Agents> {
                    groupManager.group(agentManager.activeAgents).toDto(agentManager)
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

                topic<WsRoutes.AgentBuildsSummary> { (agentId) ->
                    buildManager.buildData(agentId).buildManager.agentBuilds.map { agentBuild ->
                        BuildSummaryDto(
                            buildVersion = agentBuild.info.version,
                            detectedAt = agentBuild.detectedAt,
                            summary = pluginCaches.getData(
                                agentId,
                                agentBuild.info.version,
                                type = "build"
                            ).toJson()
                        )
                    }
                }

                topic<WsRoutes.WsVersion> { adminVersionDto }

                topic<WsRoutes.Group> { (groupId) -> groupManager[groupId] }

                topic<WsRoutes.GroupPlugins> { (groupId) ->
                    val agents = agentManager.activeAgents.filter { it.groupId == groupId }
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
