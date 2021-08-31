/**
 * Copyright 2020 EPAM Systems
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
package com.epam.drill.admin.group

import com.epam.drill.admin.*
import com.epam.drill.admin.agent.*
import com.epam.drill.admin.api.agent.*
import com.epam.drill.admin.api.group.*
import com.epam.drill.admin.api.plugin.*
import com.epam.drill.admin.api.routes.*
import com.epam.drill.admin.api.websocket.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.plugin.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.admin.router.*
import com.epam.drill.admin.websocket.*
import de.nielsfalk.ktor.swagger.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*


class GroupHandler(override val kodein: Kodein) : KodeinAware {
    private val logger = KotlinLogging.logger {}

    private val app by instance<Application>()
    private val groupManager by instance<GroupManager>()
    private val plugins by instance<Plugins>()
    private val pluginCache by instance<PluginCaches>()
    private val agentManager by instance<AgentManager>()
    private val sessions by instance<SessionStorage>()

    init {
        runBlocking {
            sendUpdates()
        }
    }

    init {
        app.routing {
            authenticate {
                val meta = "Update group"
                    .examples(
                        example("group", GroupUpdateDto(name = "Some Group"))
                    ).responds(ok<Unit>(), notFound())
                put<ApiRoot.AgentGroup, GroupUpdateDto>(meta) { (_, id), info ->
                    val statusCode = groupManager[id]?.let { group ->
                        groupManager.update(
                            group.copy(
                                name = info.name,
                                description = info.description,
                                environment = info.environment
                            )
                        )?.let { sendUpdates(listOf(it)) }
                        HttpStatusCode.OK
                    } ?: HttpStatusCode.NotFound
                    call.respond(statusCode)
                }
            }

            val metadata = "Get service group data"
                .responds(
                    ok<Any>(),
                    notFound())
            get<ApiRoot.AgentGroup.Plugin.Data>(metadata) { (pluginParent, pluginId, dataType) ->
                val groupId = pluginParent.parent.groupId
                logger.trace { "Get plugin data, groupId=${groupId}, pluginId=${pluginId}, dataType=$dataType" }
                val (statusCode, response) = if (pluginId in plugins) {
                    val agents: List<Agent> = agentManager.agentsByGroup(groupId)
                    if (agents.any()) {
                        pluginCache.retrieveMessage(
                            pluginId,
                            GroupSubscription(groupId),
                            "/group/data/$dataType"
                        ).toStatusResponsePair()
                    } else HttpStatusCode.NotFound to ErrorResponse(
                        "group $groupId not found"
                    )
                } else HttpStatusCode.NotFound to ErrorResponse("plugin '$pluginId' not found")
                logger.trace { response }
                call.respond(statusCode, response)
            }

            authenticate {
                val meta = "Register agent in defined group"
                    .examples(
                        example(
                            "agentRegistrationInfo",
                            agentRegistrationExample
                        )
                    )
                patch<ApiRoot.AgentGroup, AgentRegistrationDto>(meta) { location, regInfo ->
                    val groupId = location.groupId
                    logger.debug { "Group $groupId: registering agents..." }
                    val agents: List<Agent> = agentManager.agentsByGroup(groupId)
                    val agentInfos: List<AgentInfo> = agents.map { it.info }
                    val (status: HttpStatusCode, message: Any) = if (agents.isNotEmpty()) {
                        groupManager[groupId]?.let { groupDto ->
                            groupManager.update(
                                groupDto.copy(
                                    name = regInfo.name,
                                    description = regInfo.description,
                                    environment = regInfo.environment,
                                    systemSettings = regInfo.systemSettings
                                )
                            )?.let { sendUpdates(listOf(it)) }
                        }
                        val registeredAgentIds: List<String> = agentInfos.register(regInfo)
                        if (registeredAgentIds.count() < agentInfos.count()) {
                            val agentIds = agentInfos.map { it.id }
                            logger.error {
                                """Group $groupId: not all agents registered successfully.
                                    |Failed agents: ${agentIds - registeredAgentIds}.
                                """.trimMargin()
                            }
                        } else logger.debug { "Group $groupId: registered agents $registeredAgentIds." }
                        HttpStatusCode.OK to "$registeredAgentIds registered"
                    } else "No agents found for group $groupId".let {
                        logger.error(it)
                        HttpStatusCode.InternalServerError to it
                    }
                    call.respond(status, message)
                }
            }

            authenticate {
                val meta = "Update system settings of group"
                    .examples(
                        example(
                            "systemSettings",
                            SystemSettingsDto(
                                listOf("some package prefixes"),
                                "some session header name"
                            )
                        )
                    ).responds(
                        ok<Unit>(),
                        notFound()
                    )
                put<ApiRoot.AgentGroup.SystemSettings, SystemSettingsDto>(meta) { (group), systemSettings ->
                    val id: String = group.groupId
                    val status: HttpStatusCode = groupManager[id]?.let { group ->
                        if (systemSettings.packages.all { it.isNotBlank() }) {
                            val agentInfos: List<AgentInfo> = agentManager.agentsByGroup(id).map { it.info }
                            val updatedAgentIds = agentManager.updateSystemSettings(agentInfos, systemSettings)
                            groupManager.updateSystemSettings(group, systemSettings)?.let { sendUpdates(listOf(it)) }
                            if (updatedAgentIds.count() < agentInfos.count()) {
                                logger.error {
                                    """Group $id: not all agents updated successfully.
                                        |Failed agents: ${agentInfos - updatedAgentIds}.
                                    """.trimMargin()
                                }
                            } else logger.debug { "Group $id: updated agents $updatedAgentIds." }
                            HttpStatusCode.OK
                        } else HttpStatusCode.BadRequest
                    } ?: HttpStatusCode.NotFound
                    call.respond(status)
                }
            }

            authenticate {
                val meta = "Add plugin to group".responds(ok<Unit>(), notFound(), badRequest())
                post<ApiRoot.AgentGroup.Plugins, PluginId>(meta) { (group), (pluginId) ->
                    val groupId: String = group.groupId
                    logger.debug { "Adding plugin to group '$groupId'..." }
                    val agentInfos: List<AgentInfo> = agentManager.agentsByGroup(groupId).map { it.info }
                    val (status, msg) = if (agentInfos.isNotEmpty()) {
                        if (pluginId in plugins.keys) {
                            if (agentInfos.any { pluginId !in it.plugins }) {
                                val updatedAgentIds = agentManager.addPlugins(agentInfos, setOf(pluginId))
                                val errorAgentIds = agentInfos.map(AgentInfo::id) - updatedAgentIds
                                if (errorAgentIds.any()) {
                                    logger.error {
                                        """Group $groupId: not all agents updated successfully.
                                        |Failed agents: $errorAgentIds.
                                    """.trimMargin()
                                    }
                                } else logger.debug {
                                    "Group '$groupId': added plugin '$pluginId' to agents $updatedAgentIds."
                                }
                                HttpStatusCode.OK to "Plugin '$pluginId' added to agents $updatedAgentIds."
                            } else HttpStatusCode.Conflict to ErrorResponse(
                                "Plugin '$pluginId' already installed on all agents of group '$groupId'."
                            )
                        } else HttpStatusCode.BadRequest to ErrorResponse("Plugin '$pluginId' not found.")
                    } else HttpStatusCode.BadRequest to ErrorResponse("No agents found for group '$groupId'.")
                    call.respond(status, msg)
                }
            }
        }
    }

    private suspend fun sendUpdates(groups: Collection<GroupDto> = groupManager.all()) {
        groups.forEach { group ->
            WsRoot.Group(group.id).send(group)
            WsRoutes.Group(group.id).send(group) //TODO remove
        }
        WsRoot.Groups().send(groupManager.all())
    }

    private suspend fun List<AgentInfo>.register(
        regInfo: AgentRegistrationDto,
    ): List<String> = supervisorScope {
        map { info ->
            val agentId = info.id
            val handler = CoroutineExceptionHandler { _, e ->
                logger.error(e) { "Error registering agent $agentId" }
            }
            async(handler) {
                agentManager.register(
                    info.id,
                    regInfo.copy(name = agentId, description = agentId, environment = info.environment)
                )
                agentId
            }
        }
    }.filterNot { it.isCancelled }.map { it.await() }

    private suspend fun Any.send(message: Any) {
        val destination = app.toLocation(this)
        sessions.sendTo(destination, message)
    }
}
