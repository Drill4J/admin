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
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.config.withRole
import com.epam.drill.admin.endpoints.*
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
import org.kodein.di.ktor.closestDI

fun Routing.agentRoutes() {
    val logger = KotlinLogging.logger {}
    val agentManager by closestDI().instance<AgentManager>()
    val buildManager by closestDI().instance<BuildManager>()
    val configHandler by closestDI().instance<ConfigHandler>()
    authenticate("jwt", "api-key") {
        withRole(Role.USER, Role.ADMIN) {
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
        }
    }
}