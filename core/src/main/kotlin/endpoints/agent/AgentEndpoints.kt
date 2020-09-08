package com.epam.drill.admin.endpoints.agent


import com.epam.drill.admin.*
import com.epam.drill.admin.agent.*
import com.epam.drill.admin.api.routes.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.servicegroup.*
import com.epam.drill.common.*
import de.nielsfalk.ktor.swagger.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*

class AgentEndpoints(override val kodein: Kodein) : KodeinAware {
    private val app by instance<Application>()
    private val agentManager by instance<AgentManager>()
    private val serviceGroupManager by instance<ServiceGroupManager>()

    private val logger = KotlinLogging.logger {}

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
                        ok<AgentInfo>(),
                        HttpCodeResponse(HttpStatusCode.Conflict, emptyList())
                    )
                post<ApiRoot.Agents, AgentCreationDto>(meta) { _, payload ->
                    logger.debug { "Creating agent with id ${payload.id}..." }
                    val agentId = payload.id
                    agentManager.takeIf { it[agentId] == null }?.prepare(payload)?.run {
                        logger.info { "Created agent ${payload.id}." }
                        call.respond(HttpStatusCode.Created, this)
                    } ?: run {
                        logger.warn { "Agent ${payload.id} already exists." }
                        call.respond(HttpStatusCode.Conflict, EmptyContent)
                    }
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
                val meta = "Register agent"
                    .examples(
                        example("Petclinic", agentRegistrationExample)
                    )
                    .responds(
                        ok<Unit>(), badRequest()
                    )
                patch<ApiRoot.Agents.Agent, AgentRegistrationDto>(meta) { payload, regInfo ->
                    logger.debug { "Registering agent with id ${payload.agentId}" }
                    val agentId = payload.agentId
                    val agInfo = agentManager[agentId]
                    val (status, message) = if (agInfo != null) {
                        agInfo.register(regInfo)
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
                val meta = "Register agent in defined service group"
                    .examples(
                        example(
                            "agentRegistrationInfo",
                            agentRegistrationExample
                        )
                    )
                patch<ApiRoot.ServiceGroup, AgentRegistrationDto>(meta) { location, regInfo ->
                    val serviceGroupId = location.serviceGroupId
                    logger.debug { "Service group $serviceGroupId: registering agents..." }
                    val serviceGroup: List<AgentEntry> = agentManager.serviceGroup(serviceGroupId)
                    val agentInfos: List<AgentInfo> = serviceGroup.map { it.agent }
                    val (status: HttpStatusCode, message: Any) = if (serviceGroup.isNotEmpty()) {
                        serviceGroupManager[serviceGroupId]?.let {
                            serviceGroupManager.updateSystemSettings(it, regInfo.systemSettings)
                        }
                        val registeredAgentIds: List<String> = agentInfos.register(regInfo)
                        if (registeredAgentIds.count() < agentInfos.count()) {
                            val agentIds = agentInfos.map { it.id }
                            logger.error {
                                """Service group $serviceGroupId: not all agents registered successfully.
                                    |Failed agents: ${agentIds - registeredAgentIds}.
                                """.trimMargin()
                            }
                        } else logger.debug { "Service group $serviceGroupId: registered agents $registeredAgentIds." }
                        HttpStatusCode.OK to "$registeredAgentIds registered"
                    } else "No agents found for service group $serviceGroupId".let {
                        logger.error(it)
                        HttpStatusCode.InternalServerError to it
                    }
                    call.respond(status, message)
                }
            }

            authenticate {
                val meta = "Register all"
                    .examples(
                        example(
                            "agentRegistrationInfo",
                            agentRegistrationExample
                        )
                    )
                post<ApiRoot.Agents, AgentRegistrationDto>(meta) { _, regInfo ->
                    logger.debug { "Registering all agents" }
                    val infos = agentManager.getAllAgents().map { it.agent }
                    val registeredIds = infos.register(regInfo)
                    if (registeredIds.count() < infos.count()) {
                        val ids = infos.map { it.id }
                        logger.error {
                            "Not all agents registered successfully. Failed agents: ${ids - registeredIds}."
                        }
                    }
                    call.respond(HttpStatusCode.OK, "$registeredIds registered")
                }
            }

            authenticate {
                val meta = "Unregister agent"
                    .responds(
                        ok<Unit>(), badRequest()
                    )
                delete<ApiRoot.Agents.Agent>(meta) { payload ->
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

    private suspend fun List<AgentInfo>.register(
        regInfo: AgentRegistrationDto
    ): List<String> = supervisorScope {
        map { info ->
            val agentId = info.id
            val handler = CoroutineExceptionHandler { _, e ->
                logger.error(e) { "Error registering agent $agentId" }
            }
            async(handler) {
                info.register(regInfo.copy(name = agentId, description = agentId))
                agentId
            }
        }
    }.filterNot { it.isCancelled }.map { it.await() }

    suspend fun AgentInfo.register(regInfo: AgentRegistrationDto) {
        name = regInfo.name
        environment = regInfo.environment
        description = regInfo.description
        status = AgentStatus.ONLINE
        agentManager.apply {
            adminData(id).updateSettings(regInfo.systemSettings)
            addPlugins(regInfo.plugins)
            sync(true)
        }
    }
}
