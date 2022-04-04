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


import com.epam.drill.admin.*
import com.epam.drill.admin.agent.*
import com.epam.drill.admin.agent.config.*
import com.epam.drill.admin.api.agent.*
import com.epam.drill.admin.api.routes.*
import com.epam.drill.admin.cache.*
import com.epam.drill.admin.cache.impl.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.plugin.*
import com.epam.drill.admin.plugin.AgentCacheKey
import com.epam.drill.admin.store.*
import de.nielsfalk.ktor.swagger.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import mu.*
import org.kodein.di.*

class AgentEndpoints(override val di: DI) : DIAware {
    private val logger = KotlinLogging.logger {}

    private val app by instance<Application>()
    private val agentManager by instance<AgentManager>()
    private val buildManager by instance<BuildManager>()
    private val configHandler by instance<ConfigHandler>()

    init {
        app.routing {

            authenticate {
                val meta = "Create agent"
                    .examples(
                        example(
                            "Petclinic", AgentCreationDto(
                                id = "petclinic",
                                agentType = AgentType.JAVA,
                                name = "Petclinic"
                            )
                        )
                    )
                    .responds(
                        ok<AgentInfoDto>(),
                        HttpCodeResponse(HttpStatusCode.Conflict, emptyList())
                    )
                post<ApiRoot.Agents, AgentCreationDto>(meta) { _, payload ->
                    logger.debug { "Creating agent with id ${payload.id}..." }
                    agentManager.prepare(payload)?.run {
                        logger.info { "Created agent ${payload.id}." }
                        call.respond(HttpStatusCode.Created, toDto(agentManager))
                    } ?: run {
                        logger.warn { "Agent ${payload.id} already exists." }
                        call.respond(HttpStatusCode.Conflict, ErrorResponse("Agent '${payload.id}' already exists."))
                    }
                }
            }

            authenticate {
                val meta = "Agents metadata"
                    .examples()
                    .responds(
                        ok<String>()
                    )
                get<ApiRoot.Agents.Metadata>(meta) {
                    val metadataAgents = agentManager.all().flatMap {
                        buildManager.buildData(it.id).agentBuildManager.agentBuilds.map { agentBuild ->
                            val agentBuildKey = AgentBuildKey(it.id, agentBuild.info.version)
                            mapOf(agentBuildKey to adminStore.loadAgentMetadata(agentBuildKey))
                        }
                    }
                    call.respond(HttpStatusCode.OK, metadataAgents)
                }
            }

            authenticate {
                val meta = "Agent parameters"
                    .examples()
                    .responds(
                        ok<String>(), badRequest()
                    )
                get<ApiRoot.Agents.Parameters>(meta) { params ->
                    val (_, agentId) = params
                    val map = configHandler.load(agentId) ?: emptyMap()
                    call.respond(HttpStatusCode.OK, map)
                }
            }

            authenticate {
                val meta = "Update agent parameters"
                    .examples(
                        example(
                            "Agent parameters", mapOf(
                                "logLevel" to "DEBUG",
                                "logFile" to "Directory"
                            )
                        )
                    ).responds(
                        ok<String>(), notFound()
                    )
                patch<ApiRoot.Agents.Parameters, Map<String, String>>(meta) { location, updatedValues ->
                    val agentId = location.agentId
                    logger.debug { "Update parameters for agent with id $agentId params: $updatedValues" }
                    val (status, message) = configHandler.load(agentId)?.let { storageParameters ->
                        val newStorageParameters = storageParameters.toMutableMap()
                        updatedValues.forEach { (key, value) ->
                            newStorageParameters[key]?.let {
                                newStorageParameters[key] = it.copy(value = value)
                            } ?: logger.warn { "Cannot find and update the parameter '$key'" }
                        }
                        configHandler.store(agentId, newStorageParameters)
                        configHandler.updateAgent(agentId, updatedValues)
                        logger.debug { "Agent with id '$agentId' was updated successfully" }
                        HttpStatusCode.OK to EmptyContent
                    } ?: (HttpStatusCode.NotFound to ErrorResponse("agent '$agentId' not found"))
                    call.respond(status, message)
                }
            }

            authenticate {
                val meta = "Update agent configuration"
                    .examples(
                        example("Petclinic", agentUpdateExample)
                    )
                    .responds(
                        ok<Unit>(), badRequest()
                    )
                patch<ApiRoot.Agents.AgentInfo, AgentUpdateDto>(meta) { location, au ->
                    val agentId = location.agentId
                    logger.debug { "Update configuration for agent with id $agentId" }

                    val (status, message) = if (buildManager.agentSessions(agentId).isNotEmpty()) {
                        agentManager.updateAgent(agentId, au)
                        logger.debug { "Agent with id '$agentId' was updated successfully" }
                        HttpStatusCode.OK to EmptyContent
                    } else {
                        logger.warn { "Agent with id'$agentId' was not found" }
                        HttpStatusCode.BadRequest to ErrorResponse("agent '$agentId' not found")
                    }
                    call.respond(status, message)
                }
            }

            authenticate {
                val meta = "Register agent"
                    .examples(
                        example("Petclinic", agentRegistrationExample)
                    )
                    .responds(
                        ok<Unit>(), badRequest()
                    )
                post<ApiRoot.Agents.Agent, AgentRegistrationDto>(meta) { payload, regInfo ->
                    logger.debug { "Registering agent with id ${payload.agentId}" }
                    val agentId = payload.agentId
                    val agInfo = agentManager[agentId]
                    val (status, message) = if (agInfo != null) {
                        agentManager.register(agInfo.id, regInfo)
                        logger.debug { "Agent with id '$agentId' has been registered" }
                        HttpStatusCode.OK to EmptyContent
                    } else {
                        logger.warn { "Agent with id'$agentId' was not found" }
                        HttpStatusCode.BadRequest to ErrorResponse("Agent '$agentId' not found")
                    }
                    call.respond(status, message)
                }
            }

            authenticate {
                val meta = "Unregister agent"
                    .responds(
                        ok<Unit>(), badRequest()
                    )
                patch<ApiRoot.Agents.Agent>(meta) { payload ->
                    logger.debug { "Unregister agent with id ${payload.agentId}" }
                    val agentId = payload.agentId
                    val agInfo = agentManager[agentId]

                    val (status, message) = if (agInfo != null) {
                        agentManager.resetAgent(agInfo)
                        logger.debug { "Agent with id ${payload.agentId} has been unregistered successfully" }
                        HttpStatusCode.OK to EmptyContent
                    } else {
                        logger.warn { "Agent with id'$agentId' was not found" }
                        HttpStatusCode.BadRequest to ErrorResponse("Agent '$agentId' not found")
                    }
                    call.respond(status, message)
                }
            }

            /**
             * Also you should send action to plugin
             * {
             *     "type": "REMOVE_PLUGIN_DATA"
             * }
             */
            authenticate {
                val meta = "Remove all agent info"
                    .responds(
                        ok<Unit>(), notFound(), badRequest()
                    )
                delete<ApiRoot.Agents.Agent>(meta) { payload ->
                    val agentId = payload.agentId
                    val (status, message) = if (agentManager.removePreregisteredAgent(agentId)) {
                        HttpStatusCode.OK to "Pre registered Agent '$agentId' has been completely removed."
                    } else {
                        //TODO EPMDJ-10354 Think about ability to remove online agent
                        if (buildManager.buildStatus(agentId) == BuildStatus.OFFLINE) {
                            agentManager.removeOfflineAgent(agentId)
                            HttpStatusCode.OK to "Offline Agent '$agentId' has been completely removed."
                        } else {
                            logger.debug { "Deleting online Agent '$agentId' isn't available." }
                            HttpStatusCode.BadRequest to ErrorResponse("Deleting online Agent '$agentId' isn't availabl.e")
                        }
                    }
                    call.respond(status, message)
                }
            }
        }
    }
}
