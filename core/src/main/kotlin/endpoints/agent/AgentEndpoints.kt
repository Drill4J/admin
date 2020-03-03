package com.epam.drill.admin.endpoints.agent


import com.epam.drill.admin.*
import com.epam.drill.admin.agent.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.router.*
import com.epam.drill.common.*
import de.nielsfalk.ktor.swagger.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*

class AgentEndpoints(override val kodein: Kodein) : KodeinAware {
    private val app: Application by instance()
    private val agentManager: AgentManager by instance()

    private val logger = KotlinLogging.logger {}

    init {
        app.routing {

            authenticate {
                val updateConfigResponds = "Update agent configuration"
                    .examples(
                        example("Petclinic", agentUpdateExample)
                    )
                    .responds(
                        ok<Unit>(), badRequest()
                    )
                patch<Routes.Api.Agents.AgentInfo, AgentUpdateDto>(updateConfigResponds) { location, au ->
                    val agentId = location.agentId
                    logger.debug { "Update configuration for agent with id $agentId" }

                    val (status, message) = if (agentManager.agentSession(agentId) != null) {
                        agentManager.updateAgent(agentId, au)
                        logger.debug { "Agent with id'$agentId'was updated successfully" }
                        HttpStatusCode.OK to EmptyContent
                    } else {
                        logger.warn { "Agent with id'$agentId' was not found" }
                        HttpStatusCode.BadRequest to ErrorResponse("agent '$agentId' not found")
                    }
                    call.respond(status, message)
                }
            }

            authenticate {
                val registerAgentResponds = "Register agent"
                    .examples(
                        example("Petclinic", agentRegistrationExample)
                    )
                    .responds(
                        ok<Unit>(), badRequest()
                    )
                patch<Routes.Api.Agents.Agent, AgentRegistrationDto>(registerAgentResponds) { payload, regInfo ->
                    logger.debug { "Registering agent with id ${payload.agentId}" }
                    val agentId = payload.agentId
                    val agInfo = agentManager[agentId]
                    val (status, message) = if (agInfo != null) {
                        register(agInfo, regInfo)
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
                val registrationResponds = "Register agent in defined service group"
                    .examples(
                        example("agentRegistrationInfo",
                            agentRegistrationExample
                        )
                    )
                patch<Routes.Api.ServiceGroup, AgentRegistrationDto>(registrationResponds) { location, regInfo ->
                    val serviceGroupId = location.serviceGroupId
                    logger.debug { "Registering agents in $serviceGroupId" }
                    val serviceGroup = agentManager.serviceGroup(serviceGroupId)
                    serviceGroup.forEach { agInfo ->
                        register(agInfo.agent, regInfo.copy(name = agInfo.agent.id, description = agInfo.agent.id))
                    }

                    call.respond(
                        HttpStatusCode.OK,
                        "${serviceGroup.joinToString { it.agent.id }} registered"
                    )
                }
            }

            authenticate {
                val registerAllResponds = "Register all"
                    .examples(
                        example("agentRegistrationInfo",
                            agentRegistrationExample
                        )
                    )
                post<Routes.Api.Agents, AgentRegistrationDto>(registerAllResponds) { _, regInfo ->
                    logger.debug { "Registering all agents" }
                    val allAgents = agentManager.getAllAgents().map { it.agent }
                    allAgents.forEach { agInfo ->
                        register(agInfo, regInfo.copy(name = agInfo.id, description = agInfo.id))
                    }

                    call.respond(
                        HttpStatusCode.OK,
                        "${allAgents.joinToString { it.id }} registered"
                    )
                }
            }

            authenticate {
                val unregisterAgentResponds = "Unregister agent"
                    .responds(
                        ok<Unit>(), badRequest()
                    )
                delete<Routes.Api.Agents.Agent>(unregisterAgentResponds) { payload ->
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
        }
    }

    suspend fun register(agInfo: AgentInfo, regInfo: AgentRegistrationDto) {
        with(agInfo) {
            name = regInfo.name
            environment = regInfo.environment
            description = regInfo.description
            status = AgentStatus.ONLINE
            sessionIdHeaderName = regInfo.sessionIdHeaderName.toLowerCase()
        }
        agentManager.apply {
            adminData(agInfo.id).apply { packagesPrefixes = regInfo.packagesPrefixes }
            addPlugins(agInfo, regInfo.plugins)
            agInfo.sync(true)
        }
    }
}
