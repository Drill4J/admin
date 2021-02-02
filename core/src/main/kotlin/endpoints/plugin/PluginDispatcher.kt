/**
 * Copyright 2020 EPAM Systems
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
package com.epam.drill.admin.endpoints.plugin

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.api.agent.*
import com.epam.drill.admin.api.plugin.*
import com.epam.drill.admin.api.routes.*
import com.epam.drill.admin.api.websocket.*
import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.plugin.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.api.*
import com.epam.drill.plugin.api.end.*
import com.epam.drill.plugin.api.message.*
import de.nielsfalk.ktor.swagger.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*
import kotlin.reflect.full.*

internal class PluginDispatcher(override val kodein: Kodein) : KodeinAware {
    private val logger = KotlinLogging.logger {}

    private val app by instance<Application>()
    private val plugins by instance<Plugins>()
    private val pluginCache by instance<PluginCaches>()
    private val agentManager by instance<AgentManager>()

    suspend fun processPluginData(
        agentInfo: AgentInfo,
        instanceId: String,
        pluginData: String
    ) {
        val message = MessageWrapper.serializer().parse(pluginData)
        val pluginId = message.pluginId
        plugins[pluginId]?.let {
            val agentEntry = agentManager.entryOrNull(agentInfo.id)!!
            agentEntry[pluginId]?.run {
                processData(instanceId, message.drillMessage.content)
            } ?: logger.error { "Plugin $pluginId not initialized for agent ${agentInfo.id}!" }
        } ?: logger.error { "Plugin $pluginId not loaded!" }
    }

    init {
        app.routing {
            authenticate {
                val meta = "Dispatch Plugin Action"
                    .examples(
                        example("action", "some action name")
                    )
                    .responds(
                        ok<String>(
                            example("")
                        ), notFound()
                    )
                post<ApiRoot.Agents.DispatchPluginAction, String>(meta) { payload, action ->
                    val (_, agentId, pluginId) = payload
                    logger.debug { "Dispatch action plugin with id $pluginId for agent with id $agentId" }
                    val agentEntry = agentManager.entryOrNull(agentId)
                    val (statusCode, response) = agentEntry?.run {
                        val plugin: Plugin? = this@PluginDispatcher.plugins[pluginId]
                        if (plugin != null) {
                            if (agentEntry.agent.status == AgentStatus.ONLINE) {
                                this[pluginId]?.let { adminPart ->
                                    val result = adminPart.processAction(action, agentManager::agentSessions)
                                    val statusResponse = result.toStatusResponse()
                                    HttpStatusCode.fromValue(statusResponse.code) to statusResponse
                                } ?: HttpStatusCode.BadRequest to ErrorResponse(
                                    "Cannot dispatch action: plugin $pluginId not initialized for agent $agentId."
                                )
                            } else HttpStatusCode.BadRequest to ErrorResponse(
                                "Cannot dispatch action for plugin '$pluginId', agent '$agentId' is not online."
                            )
                        } else HttpStatusCode.NotFound to ErrorResponse("Plugin with id $pluginId not found")
                    } ?: HttpStatusCode.NotFound to ErrorResponse("Agent with id $pluginId not found")
                    logger.info { "$response" }
                    sendResponse(response, statusCode)
                }
            }

            authenticate {
                val meta = "Dispatch defined plugin actions in defined service group"
                    .examples(
                        example("action", "some action name")
                    )
                    .responds(
                        ok<String>(
                            example("")
                        ), notFound()
                    )
                post<ApiRoot.ServiceGroup.Plugin.DispatchAction, String>(meta) { pluginParent, action ->
                    val pluginId = pluginParent.parent.pluginId
                    val serviceGroupId = pluginParent.parent.parent.serviceGroupId
                    val agents = agentManager.serviceGroup(serviceGroupId)
                    logger.debug { "Dispatch action plugin with id $pluginId for agents with serviceGroupId $serviceGroupId" }
                    val (statusCode, response) = plugins[pluginId]?.let {
                        processMultipleActions(
                            agents,
                            pluginId,
                            action
                        )
                    } ?: HttpStatusCode.NotFound to ErrorResponse("Plugin $pluginId not found.")
                    logger.trace { "$response" }
                    call.respond(statusCode, response)
                }
            }

            get<ApiRoot.Agents.PluginData> { (_, agentId, pluginId, dataType) ->
                logger.debug { "Get plugin data, agentId=$agentId, pluginId=$pluginId, dataType=$dataType" }
                val dp: Plugin? = plugins[pluginId]
                val agentInfo = agentManager[agentId]
                val agentEntry = agentManager.entryOrNull(agentId)
                val (statusCode: HttpStatusCode, response: Any) = when {
                    (dp == null) -> HttpStatusCode.NotFound to ErrorResponse("Plugin '$pluginId' not found")
                    (agentInfo == null) -> HttpStatusCode.NotFound to ErrorResponse("Agent '$agentId' not found")
                    (agentEntry == null) -> HttpStatusCode.NotFound to ErrorResponse("Data for agent '$agentId' not found")
                    else -> AgentSubscription(agentId, agentInfo.buildVersion).let { subscription ->
                        pluginCache.retrieveMessage(
                            pluginId,
                            subscription,
                            "/data/$dataType"
                        ).toStatusResponsePair()
                    }
                }
                sendResponse(response, statusCode)
            }

            authenticate {
                val meta = "Add new plugin"
                    .examples(
                        example("pluginId", PluginId("some plugin id"))
                    )
                    .responds(
                        ok<String>(
                            example("result", "Plugin was added")
                        ), badRequest()
                    )
                post<ApiRoot.Agents.Plugins, PluginId>(meta) { params, pluginIdObject ->
                    val (_, agentId) = params
                    logger.debug { "Add new plugin for agent with id $agentId" }
                    val (status, msg) = when (pluginIdObject.pluginId) {
                        in plugins.keys -> {
                            if (agentId in agentManager) {
                                val agentInfo = agentManager[agentId]!!
                                if (pluginIdObject.pluginId in agentInfo.plugins) {
                                    HttpStatusCode.BadRequest to
                                            ErrorResponse("Plugin '${pluginIdObject.pluginId}' is already in agent '$agentId'")
                                } else {
                                    agentManager.addPlugins(agentInfo.id, setOf(pluginIdObject.pluginId))
                                    HttpStatusCode.OK to "Plugin '${pluginIdObject.pluginId}' was added to agent '$agentId'"
                                }
                            } else {
                                HttpStatusCode.BadRequest to ErrorResponse("Agent '$agentId' not found")
                            }
                        }
                        else -> HttpStatusCode.BadRequest to ErrorResponse("Plugin ${pluginIdObject.pluginId} not found.")
                    }
                    logger.debug { msg }
                    call.respond(status, msg)
                }
            }
            authenticate {
                val meta = "Toggle Plugin"
                    .responds(
                        ok<Unit>(), notFound()
                    )
                post<ApiRoot.Agents.TogglePlugin>(meta) { params ->
                    val (_, agentId, pluginId) = params
                    logger.debug { "Toggle plugin with id $pluginId for agent with id $agentId" }
                    val dp: Plugin? = plugins[pluginId]
                    val session = agentManager.agentSessions(agentId)
                    val (statusCode, response) = when {
                        (dp == null) -> HttpStatusCode.NotFound to ErrorResponse("plugin with id $pluginId not found")
                        (session.isEmpty()) -> HttpStatusCode.NotFound to ErrorResponse("agent with id $agentId not found")
                        else -> {
                            session.applyEach {
                                sendToTopic<Communication.Plugin.ToggleEvent, TogglePayload>(
                                    message = TogglePayload(pluginId)
                                )
                            }
                            HttpStatusCode.OK to EmptyContent
                        }
                    }
                    logger.debug { response }
                    call.respond(statusCode, response)
                }
            }

        }
    }

    private suspend fun PipelineContext<Unit, ApplicationCall>.sendResponse(
        response: Any,
        statusCode: HttpStatusCode
    ) = when (response) {
        is String -> call.respondText(
            text = response,
            contentType = ContentType.Application.Json,
            status = statusCode
        )
        is ByteArray -> call.respondBytes(
            bytes = response,
            contentType = ContentType.MultiPart.Any,
            status = statusCode
        )
        else -> call.respond(
            status = statusCode,
            message = response
        )
    }

    private suspend fun processMultipleActions(
        agents: List<AgentEntry>,
        pluginId: String,
        action: String
    ): Pair<HttpStatusCode, List<JsonElement>> {
        val statusesResponse: List<JsonElement> = agents.mapNotNull { entry: AgentEntry ->
            when (entry.agent.status) {
                AgentStatus.ONLINE -> entry[pluginId]?.run {
                    val adminActionResult = processAction(action, agentManager::agentSessions)
                    adminActionResult.toStatusResponse()
                }
                AgentStatus.NOT_REGISTERED, AgentStatus.OFFLINE -> null
                else -> "Agent ${entry.agent.id} is in the wrong state - ${entry.agent.status}".run {
                    StatusMessageResponse(
                        code = HttpStatusCode.Conflict.value,
                        message = this
                    )
                }
            }
        }.map { WithStatusCode.serializer() toJson it }
        return HttpStatusCode.OK to statusesResponse
    }
}

private fun Any.toStatusResponse(): WithStatusCode = when (this) {
    is ActionResult -> when (val d = data) {
        is String -> StatusMessageResponse(code, d)
        else -> StatusResponse(code, d.actionToJson())
    }
    else -> StatusResponse(HttpStatusCode.OK.value, actionToJson())
}

private fun Any.actionToJson(): JsonElement = actionSerializerOrNull()!! toJson this
