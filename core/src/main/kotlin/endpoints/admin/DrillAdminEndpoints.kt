package com.epam.drill.admin.endpoints.admin

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.agent.logging.*
import com.epam.drill.admin.api.*
import com.epam.drill.admin.api.agent.*
import com.epam.drill.admin.api.routes.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.api.*
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

    init {
        app.routing {
            authenticate {
                val meta = "Unload plugin"
                    .responds(
                        ok<Unit>(), notFound(), badRequest()
                    )
                delete<ApiRoot.Agents.Plugin>(meta) { payload ->
                    val (_, agentId, pluginId) = payload
                    logger.debug { "Unload plugin with id $pluginId for agent with id $agentId" }
                    val drillAgent = agentManager.agentSessions(agentId)
                    val agentPluginPartFile = plugins[pluginId]?.agentPluginPart

                    val (status, response) = when {
                        drillAgent.isEmpty() -> {
                            logger.warn { "Drill agent is absent" }
                            HttpStatusCode.NotFound to ErrorResponse("Can't find the agent '$agentId'")
                        }
                        agentPluginPartFile == null -> {
                            logger.warn { "Agent plugin part file is absent" }
                            HttpStatusCode.NotFound to ErrorResponse("Can't find the plugin '$pluginId' in the agent '$agentId'")
                        }
                        else -> {
                            drillAgent.applyEach {
                                send(
                                    com.epam.drill.common.json.stringify(
                                        com.epam.drill.common.Message.serializer(),
                                        com.epam.drill.common.Message(
                                            com.epam.drill.common.MessageType.MESSAGE,
                                            "/plugins/unload",
                                            pluginId.encodeToByteArray()
                                        )
                                    )
                                )
                            }
                            logger.info { "Unload plugin with id $pluginId for agent with id $agentId was successfully" }
                            //TODO: implement the agent-side plugin unloading, remove plugin from AgentInfo
                            HttpStatusCode.OK to EmptyContent
                        }
                    }
                    call.respond(status, response)
                }

            }

            authenticate {
                val meta = "Agent Toggle StandBy"
                    .responds(
                        ok<Unit>(), notFound(), badRequest()
                    )
                post<ApiRoot.Agents.ToggleAgent>(meta) { params ->
                    val (_, agentId) = params
                    logger.info { "Toggle agent $agentId" }
                    val (status, response) = agentManager[agentId]?.let { agentInfo ->
                        when (agentInfo.status) {
                            AgentStatus.OFFLINE -> AgentStatus.ONLINE
                            AgentStatus.ONLINE -> AgentStatus.OFFLINE
                            else -> null
                        }?.let { newStatus ->
                            agentManager.agentSessions(agentId).applyEach {
                                val toggleValue = newStatus == AgentStatus.ONLINE
                                agentInfo.plugins.map { pluginId ->
                                    sendToTopic<Communication.Plugin.ToggleEvent>(
                                        com.epam.drill.common.TogglePayload(pluginId, toggleValue)
                                    )
                                }.forEach { it.await() } //TODO coroutine scope (supervisor)
                            }
                            with(agentManager) {
                                entryOrNull(agentId)!!.updateAgent {
                                    it.copy(status = newStatus)
                                }.commitChanges()
                            }
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
                val meta = "Configure agent logging levels"
                    .examples(
                        example("Agent logging configuration", defaultLoggingConfig)
                    )
                    .responds(
                        ok<Unit>(), notFound(), badRequest()
                    )
                put<ApiRoot.Agents.AgentLogging, LoggingConfigDto>(meta) { (_, agentId), loggingConfig ->
                    logger.debug { "Attempt to configure logging levels for agent with id $agentId" }
                    loggingHandler.updateConfig(agentId, loggingConfig)
                    logger.debug { "Successfully sent request for logging levels configuration for agent with id $agentId" }
                    call.respond(HttpStatusCode.OK, EmptyContent)
                }
            }

            authenticate {
                val meta = "Update system settings"
                    .examples(
                        example(
                            "systemSettings",
                            SystemSettingsDto(
                                listOf("some package prefixes"),
                                "some session header name"
                            )
                        )
                    )
                    .responds(
                        ok<String>(), badRequest()
                    )
                put<ApiRoot.Agents.SystemSettings, SystemSettingsDto>(meta) { params, settingsDto ->
                    val (_, agentId) = params
                    settingsDto.takeIf { it.packages.none(String::isBlank) }?.let {
                        agentManager.updateSystemSettings(agentId, it)
                        call.respond(HttpStatusCode.OK, EmptyContent)
                    } ?: call.respond(HttpStatusCode.BadRequest, "Package prefixes contain an empty value.")
                }
            }
        }
    }
}
