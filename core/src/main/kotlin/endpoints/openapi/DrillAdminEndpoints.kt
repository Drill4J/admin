package com.epam.drill.admin.endpoints.openapi

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.agent.logging.*
import com.epam.drill.admin.api.routes.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.api.*
import com.epam.drill.api.dto.*
import com.epam.drill.common.*
import de.nielsfalk.ktor.swagger.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.response.*
import io.ktor.routing.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*

class DrillAdminEndpoints(override val kodein: Kodein) : KodeinAware {
    private val logger = KotlinLogging.logger {}
    private val app by instance<Application>()
    private val agentManager by instance<AgentManager>()
    private val loggingHandler by instance<LoggingHandler>()
    private val plugins by instance<Plugins>()
    private val handler by instance<AdminEndpointsHandler>()

    init {
        app.routing {
            authenticate {
                val unloadPluginResponds = "Unload plugin"
                    .responds(
                        ok<Unit>(), notFound(), badRequest()
                    )
                delete<ApiRoot.Agents.Plugin>(unloadPluginResponds) { payload ->
                    val (_, agentId, pluginId) = payload
                    logger.debug { "Unload plugin with id $pluginId for agent with id $agentId" }
                    val drillAgent = agentManager.agentSession(agentId)
                    val agentPluginPartFile = plugins[pluginId]?.agentPluginPart

                    val (status, response) = when {
                        drillAgent == null -> {
                            logger.warn { "Drill agent is absent" }
                            HttpStatusCode.NotFound to ErrorResponse("Can't find the agent '$agentId'")
                        }
                        agentPluginPartFile == null -> {
                            logger.warn { "Agent plugin part file is absent" }
                            HttpStatusCode.NotFound to ErrorResponse("Can't find the plugin '$pluginId' in the agent '$agentId'")
                        }
                        else -> {
                            drillAgent.send(
                                Message.serializer() stringify Message(
                                    MessageType.MESSAGE,
                                    "/plugins/unload",
                                    pluginId.encodeToByteArray()
                                )
                            )
                            logger.info { "Unload plugin with id $pluginId for agent with id $agentId was successfully" }
                            //TODO: implement the agent-side plugin unloading, remove plugin from AgentInfo
                            HttpStatusCode.OK to EmptyContent
                        }
                    }
                    call.respond(status, response)
                }

            }

            authenticate {
                val agentToggleStandByResponds = "Agent Toggle StandBy"
                    .responds(
                        ok<Unit>(), notFound(), badRequest()
                    )
                post<ApiRoot.Agents.ToggleAgent>(agentToggleStandByResponds) { params ->
                    val (_, agentId) = params
                    logger.info { "Toggle agent $agentId" }
                    val (status, response) = agentManager[agentId]?.let { agentInfo ->
                        when (agentInfo.status) {
                            AgentStatus.OFFLINE -> AgentStatus.ONLINE
                            AgentStatus.ONLINE -> AgentStatus.OFFLINE
                            else -> null
                        }?.let { newStatus ->
                            agentManager.agentSession(agentId)?.apply {
                                val toggleValue = newStatus == AgentStatus.ONLINE
                                agentInfo.plugins.filter { it.enabled }.map {
                                    sendToTopic<Communication.Plugin.ToggleEvent>(TogglePayload(it.id, toggleValue))
                                }.forEach { it.await() } //TODO coroutine scope (supervisor)
                            }
                            agentInfo.status = newStatus
                            with(agentManager) { agentInfo.commitChanges() }
                            logger.info { "Agent $agentId toggled, new status - $newStatus." }
                            HttpStatusCode.OK to EmptyContent
                        } ?: HttpStatusCode.Conflict to ErrorResponse(
                            "Cannot toggle agent $agentId on status ${agentInfo.status}"
                        )
                    } ?: HttpStatusCode.NotFound to EmptyContent
                    call.respond(status, response)
                }
            }

            authenticate {
                val loggingResponds = "Configure agent logging levels"
                    .examples(
                        example("Agent logging configuration", defaultLoggingConfig)
                    )
                    .responds(
                        ok<Unit>(), notFound(), badRequest()
                    )
                put<ApiRoot.Agents.AgentLogging, LoggingConfig>(loggingResponds) { (_, agentId), loggingConfig ->
                    logger.debug { "Attempt to configure logging levels for agent with id $agentId" }
                    loggingHandler.updateConfig(agentId, loggingConfig)
                    logger.debug { "Successfully sent request for logging levels configuration for agent with id $agentId" }
                    call.respond(HttpStatusCode.OK, EmptyContent)
                }
            }

            authenticate {
                val systemSettingsResponds = "Update system settings"
                    .examples(
                        example(
                            "systemSettings",
                            SystemSettingsDto(listOf("some package prefixes"), "some session header name")
                        )
                    )
                    .responds(
                        ok<String>(),
                        badRequest()
                    )
                put<ApiRoot.Agents.SystemSettings, SystemSettingsDto>(systemSettingsResponds) { params, systemSettings ->
                    val (_, agentId) = params
                    val statusCode = handler.updateSystemSettings(agentId, systemSettings)

                    val response: Any = when (statusCode) {
                        HttpStatusCode.OK -> EmptyContent
                        HttpStatusCode.BadRequest -> ErrorResponse("Package prefixes contains empty value")
                        else -> ErrorResponse("Request handle with exception")
                    }
                    logger.info { "System settings update was finished with result: $response" }
                    call.respond(statusCode, response)
                }
            }
        }
    }
}
