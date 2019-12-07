package com.epam.drill.endpoints.agent


import com.epam.drill.agentmanager.*
import com.epam.drill.common.*
import com.epam.drill.common.ws.*
import com.epam.drill.endpoints.*
import com.epam.drill.router.*
import com.epam.drill.system.*
import com.epam.drill.util.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*
import java.util.concurrent.atomic.*

class AgentEndpoints(override val kodein: Kodein) : KodeinAware {
    private val app: Application by instance()
    private val agentManager: AgentManager by instance()

    private val logger = KotlinLogging.logger {}

    init {
        app.routing {

            authenticate {
                post<Routes.Api.UpdateAgentConfig> { (agentId) ->
                    logger.debug { "Update configuration for agent with id $agentId" }

                    val (status, message) = if (agentManager.agentSession(agentId) != null) {
                        val au = call.parse(AgentInfoWebSocket.serializer())
                        agentManager.updateAgent(agentId, au)
                        if (au.sessionIdHeaderName.isNotEmpty())
                            agentManager.agentSession(au.id)?.apply {
                                sendToTopic(
                                    "/agent/config",
                                    ServiceConfig(app.securePort(), au.sessionIdHeaderName.toLowerCase())
                                ).call()
                            }
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
                post<Routes.Api.Agent.ActivateAgents> { (grp) ->
                    val counter = AtomicInteger()
                    val filter = agentManager.getAllAgents().filter { it.agent.serviceGroup == grp }

                    filter.map {
                        app.launch {
                            it.agentSession.sendToTopic("/ping", "").await()
                            counter.incrementAndGet()
                        }
                    }.forEach {
                        it.join()
                    }
                    call.respondText(counter.toString())
                }
            }
            authenticate {
                post<Routes.Api.Agent.RegisterAgent> { payload ->
                    logger.debug { "Registering agent with id ${payload.agentId}" }
                    val agentId = payload.agentId
                    val agInfo = agentManager[agentId]
                    val (status, message) = if (agInfo != null) {
                        val regInfo = call.parse(AgentRegistrationInfo.serializer())
                        with(agInfo) {
                            name = regInfo.name
                            groupName = regInfo.group
                            description = regInfo.description
                            status = AgentStatus.ONLINE
                            sessionIdHeaderName = regInfo.sessionIdHeaderName
                        }
                        agentManager.adminData(agentId).apply { packagesPrefixes = regInfo.packagesPrefixes }
                        agentManager.addPlugins(agInfo, regInfo.plugins)
                        agentManager.sync(agInfo, true)
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
                post<Routes.Api.Agent.UnregisterAgent> { ll ->
                    logger.debug { "Unregister agent with id ${ll.agentId}" }
                    val agentId = ll.agentId
                    val agInfo = agentManager[agentId]

                    val (status, message) = if (agInfo != null) {
                        agentManager.resetAgent(agInfo)
                        logger.debug { "Agent with id ${ll.agentId} has been unregistered successfully" }
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
