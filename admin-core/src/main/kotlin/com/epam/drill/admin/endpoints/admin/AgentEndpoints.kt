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

            authenticate("jwt", "basic") {

                get<ApiRoot.Agents.Metadata>(
                    "Agents metadata"
                        .examples()
                        .responds(
                            ok<String>()
                        )
                ) {
                    val metadataAgents = agentManager.all().flatMap {
                        buildManager.buildData(it.id).agentBuildManager.agentBuilds.map { agentBuild ->
                            val agentBuildKey = AgentBuildKey(it.id, agentBuild.info.version)
                            mapOf(agentBuildKey to adminStore.loadAgentMetadata(agentBuildKey))
                        }
                    }
                    call.respond(HttpStatusCode.OK, metadataAgents)
                }




                post<ApiRoot.Agents.Agent, AgentRegistrationDto>(
                    "Register agent"
                        .examples(
                            example("Petclinic", agentRegistrationExample)
                        )
                        .responds(
                            ok<Unit>(), badRequest()
                        )
                ) { payload, regInfo ->
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


                /**
                 * Also you should send action to plugin
                 * {
                 *     "type": "REMOVE_PLUGIN_DATA"
                 * }
                 */
                delete<ApiRoot.Agents.Agent>(
                    "Remove all agent info"
                        .responds(
                            ok<Unit>(), notFound(), badRequest()
                        )
                ) { payload ->
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
