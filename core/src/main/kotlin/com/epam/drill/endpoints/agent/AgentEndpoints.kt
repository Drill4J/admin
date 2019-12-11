package com.epam.drill.endpoints.agent


import com.epam.drill.*
import com.epam.drill.agentmanager.*
import com.epam.drill.common.*
import com.epam.drill.endpoints.*
import com.epam.drill.router.*
import de.nielsfalk.ktor.swagger.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.routing.*
import kotlinx.serialization.*
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
                        example("Petclinic", agentInfoWebSocketExample)
                    )
                    .responds(
                        ok<String>(
                            example("result", "Agent with such id was updated successfully")
                        ), badRequest()
                    )
                post<Routes.Api.UpdateAgentConfig, AgentInfoWebSocket>(updateConfigResponds) { location, au ->
                    val agentId = location.agentId
                    logger.debug { "Update configuration for agent with id $agentId" }

                    val (status, message) = if (agentManager.agentSession(agentId) != null) {
                        agentManager.updateAgent(agentId, au)
                        logger.debug { "Agent with id'$agentId'was updated successfully" }
                        HttpStatusCode.OK to "agent '$agentId' was updated"
                    } else {
                        logger.warn { "Agent with id'$agentId' was not found" }
                        HttpStatusCode.BadRequest to "agent '$agentId' not found"
                    }
                    call.respondJsonIfErrorsOccured(status, message)
                }
            }


            authenticate {
                val registerAgentResponds = "Registering agent"
                    .examples(
                        example("Petclinic", agentRegistrationExample)
                    )
                    .responds(
                        ok<String>(
                            example("result", "Agent with such id has been registered")
                        ), badRequest()
                    )
                post<Routes.Api.Agent.RegisterAgent, AgentRegistrationInfo>(registerAgentResponds) { payload, regInfo->
                    logger.debug { "Registering agent with id ${payload.agentId}" }
                    val agentId = payload.agentId
                    val agInfo = agentManager[agentId]
                    val (status, message) = if (agInfo != null) {
                        register(agInfo, regInfo)
                        logger.debug { "Agent with id '$agentId' has been registered" }
                        HttpStatusCode.OK to "Agent '$agentId' has been registered"
                    } else {
                        logger.warn { "Agent with id'$agentId' was not found" }
                        HttpStatusCode.BadRequest to "Agent '$agentId' not found"
                    }
                    call.respondJsonIfErrorsOccured(status, message)
                }
            }

            authenticate {
                val registrationResponds = "Registering agent in defined service group"
                    .examples(
                        example("agentRegistrationInfo", agentRegistrationExample)
                    )
                post<Routes.Api.ServiceGroup.Register, AgentRegistrationInfo>(registrationResponds) { params, regInfo ->
                    val (serviceGroupId) = params
                    logger.debug { "Registering agents in $serviceGroupId" }
                    val serviceGroup = agentManager.serviceGroup(serviceGroupId)
                    serviceGroup.forEach { agInfo ->
                        register(agInfo.agent, regInfo.copy(name = agInfo.agent.id, description = agInfo.agent.id))
                    }

                    call.respondJsonIfErrorsOccured(
                        HttpStatusCode.OK,
                        "${serviceGroup.joinToString { it.agent.id }} registered"
                    )
                }
            }

            authenticate {
                val registerAllResponds = "Register all"
                    .examples(
                        example("agentRegistrationInfo", agentRegistrationExample)
                    )
                post<Routes.Api.RegisterAll, AgentRegistrationInfo>(registerAllResponds) { _, regInfo ->
                    logger.debug { "Registering all agents" }
                    val allAgents = agentManager.getAllAgents().map { it.agent }
                    allAgents.forEach { agInfo ->
                        register(agInfo, regInfo.copy(name = agInfo.id, description = agInfo.id))
                    }

                    call.respondJsonIfErrorsOccured(HttpStatusCode.OK, "${allAgents.joinToString { it.id }} registered")
                }
            }

            authenticate {
                val unregisterAgentResponds = "Unregister agent"
                    .responds(
                        ok<String>(
                            example("result", "Agent with such id has been unregistered successfully")
                        ), badRequest()
                    )
                post<Routes.Api.Agent.UnregisterAgent>(unregisterAgentResponds) { payload ->
                    logger.debug { "Unregister agent with id ${payload.agentId}" }
                    val agentId = payload.agentId
                    val agInfo = agentManager[agentId]

                    val (status, message) = if (agInfo != null) {
                        agentManager.resetAgent(agInfo)
                        logger.debug { "Agent with id ${payload.agentId} has been unregistered successfully" }
                        HttpStatusCode.OK to "Agent '$agentId' has been unregistered"
                    } else {
                        logger.warn { "Agent with id'$agentId' was not found" }
                        HttpStatusCode.BadRequest to "Agent '$agentId' not found"
                    }
                    call.respondJsonIfErrorsOccured(status, message)
                }
            }
        }
    }

    suspend fun register(agInfo: AgentInfo, regInfo: AgentRegistrationInfo) {
        with(agInfo) {
            name = regInfo.name
            groupName = regInfo.group
            description = regInfo.description
            status = AgentStatus.ONLINE
            sessionIdHeaderName = regInfo.sessionIdHeaderName.toLowerCase()
        }
        agentManager.adminData(agInfo.id).apply { packagesPrefixes = regInfo.packagesPrefixes }
        agentManager.addPlugins(agInfo, regInfo.plugins)
        agentManager.sync(agInfo, true)
    }
}

@Serializable
data class AgentRegistrationInfo(
    val name: String,
    val description: String,
    val group: String = "",
    val packagesPrefixes: List<String>,
    val sessionIdHeaderName: String = "",
    val plugins: List<String> = emptyList()
)
