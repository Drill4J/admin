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
package com.epam.drill.admin.group

import com.epam.drill.admin.agent.AgentInfo
import com.epam.drill.admin.agentRegistrationExample
import com.epam.drill.admin.api.agent.AgentRegistrationDto
import com.epam.drill.admin.api.group.GroupDto
import com.epam.drill.admin.api.group.GroupUpdateDto
import com.epam.drill.admin.api.routes.ApiRoot
import com.epam.drill.admin.api.routes.WsRoot
import com.epam.drill.admin.api.websocket.GroupSubscription
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.plugin.PluginCaches
import com.epam.drill.admin.plugins.Plugins
import com.epam.drill.admin.router.WsRoutes
import com.epam.drill.admin.websocket.SessionStorage
import de.nielsfalk.ktor.swagger.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import mu.KotlinLogging
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance

/**
 * REST Controller for groups
 */
class GroupHandler(override val di: DI) : DIAware {
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
                    notFound()
                )
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
