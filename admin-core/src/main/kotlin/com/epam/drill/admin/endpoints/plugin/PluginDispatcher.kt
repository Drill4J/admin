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
package com.epam.drill.admin.endpoints.plugin

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.api.agent.AgentStatus.*
import com.epam.drill.admin.api.agent.BuildStatus.*
import com.epam.drill.admin.api.routes.*
import com.epam.drill.admin.api.websocket.*
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.config.withRole
import com.epam.drill.admin.build.*
import com.epam.drill.admin.build.AgentBuildData
import com.epam.drill.admin.cache.*
import com.epam.drill.admin.cache.impl.*
import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.plugin.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.admin.store.*
import com.epam.drill.admin.websocket.*
import com.epam.drill.common.message.*
import com.epam.drill.common.ws.*
import com.epam.drill.plugin.api.end.*
import de.nielsfalk.ktor.swagger.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import mu.*
import org.kodein.di.*
import java.io.*
import kotlin.reflect.*
import com.epam.drill.plugins.test2code.api.routes.Routes
import org.kodein.di.ktor.closestDI

internal class PluginDispatcher(override val di: DI) : DIAware {
    private val logger = KotlinLogging.logger {}

    private val app by instance<Application>()
    private val locationRouteService by instance<LocationRouteService>()
    private val plugins by instance<Plugins>()
    private val pluginCache by instance<PluginCaches>()
    private val agentManager by instance<AgentManager>()
    private val buildManager by instance<BuildManager>()
    private val cacheService by instance<CacheService>()

    /**
     * Receive data from agents
     * @param agentInfo the information about the agent
     * @param instanceId the agent instance ID
     * @param pluginData the application data
     */
    suspend fun processPluginData(
        agentInfo: AgentInfo,
        instanceId: String,
        pluginData: String,
    ) {
        val message = DrillMessageWrapper.serializer().parse(pluginData)
        val pluginId = message.pluginId
        plugins[pluginId]?.let {
            val agentEntry = agentManager.entryOrNull(agentInfo.id)!!
            agentEntry[pluginId]?.run {
                processData(instanceId, agentInfo.build.version, message.drillMessage.content)
            } ?: logger.error { "Plugin $pluginId not initialized for agent ${agentInfo.id}!" }
        } ?: logger.error { "Plugin $pluginId not loaded!" }
    }

    suspend fun dispatchAction(
        agentInfo: AgentInfo,
        instanceId: String,
        message: String,
    ) = run {
        val wrapper = DrillMessageWrapper.serializer().parse(message)
        val pluginId = wrapper.pluginId
        val action = wrapper.drillMessage.content
        logger.info { "Attempting to dispatch action from agent ${agentInfo.debugString(instanceId)} : $action" }
        plugins[pluginId]?.let {
            val agentEntry = agentManager.entryOrNull(agentInfo.id)!!
            agentEntry[pluginId]?.run {
                val result = processAction(action, buildManager::agentSessions)
                logger.info { "Response ${result.toStatusResponse()} " }
            } ?: logger.error { "Plugin $pluginId not initialized for agent ${agentInfo.id}!" }
        } ?: logger.error { "Plugin $pluginId not loaded!" }
    }

    fun initRouting(routing: Routing) {
        with(routing) {
            plugins.forEach { (pluginId, plugin) ->
                val pluginRouters = pluginRoutes(pluginId)
                logger.debug { "start register routes for '$pluginId' size ${pluginRouters.size} $pluginRouters..." }
                pluginRouters.forEach { destination ->
                    createPluginGetRoute(destination)
                }
            }
            authenticate("jwt", "basic") {
                withRole(Role.USER) {
                    post<ApiRoot.Agents.DispatchPluginAction, String>(
                        "Dispatch Plugin Action"
                            .examples(
                                example("action", "some action name")
                            )
                            .responds(
                                ok<String>(
                                    example("")
                                ), notFound()
                            )
                    ) { payload, action ->
                        val (_, agentId, pluginId) = payload
                        logger.debug { "Dispatch action plugin with id $pluginId for agent with id $agentId" }
                        val agent = agentManager.entryOrNull(agentId)
                        val (statusCode, response) = agent?.run {
                            val plugin: Plugin? = this@PluginDispatcher.plugins[pluginId]
                            if (plugin != null) {
                                val buildStatus = buildManager.buildStatus(agentId)
                                val adminPluginPart = this[pluginId]
                                if (info.agentStatus == REGISTERED) {
                                    adminPluginPart?.let { adminPart ->
                                        val result = adminPart.processAction(action, buildManager::agentSessions)
                                        val statusResponse = result.toStatusResponse()
                                        HttpStatusCode.fromValue(statusResponse.code) to statusResponse
                                    } ?: (HttpStatusCode.BadRequest to ErrorResponse(
                                        "Cannot dispatch action: plugin $pluginId not initialized for agent $agentId."
                                    )).also { logger.warn { "BadRequest with action: $action" } }
                                } else HttpStatusCode.BadRequest to ErrorResponse(
                                    "Cannot dispatch action for plugin '$pluginId', agent '$agentId' is ${info.agentStatus}," +
                                            " status of build is $buildStatus."
                                ).also { logger.warn { "BadRequest with action: $action" } }
                            } else HttpStatusCode.NotFound to ErrorResponse("Plugin with id $pluginId not found")
                        } ?: (HttpStatusCode.NotFound to ErrorResponse("Agent with id $pluginId not found"))
                        logger.info { "response for '$agentId': $response" }
                        sendResponse(response, statusCode)
                    }

                    // TODO EPMDJ-8531 Support multipart/form-data in ktor-swagger
                    post<ApiRoot.Agents.ProcessData, MultiPartData>(
                        "Process plugin data"
                            .examples(
                                example(
                                    "Petclinic",
                                    """Multipart form-data with key-value pairs: 
                                    "action":{"type":"IMPORT_COVERAGE"},
                                    "data": File(jacoco.exec)
                                """.trimIndent()
                                )
                            )
                            .description("To try out this request, please use the Postman")
                            .responds(ok<String>(), badRequest())
                    ) { (_, agentId, pluginId), data ->
                        val parts: List<PartData> = data.readAllParts()
                        val action = (parts.find { it.name == "action" } as PartData.FormItem).value
                        val inputStream = (parts.find { it.name == "data" } as PartData.FileItem).streamProvider()
                        logger.debug { "Process data with plugin with id $pluginId for agent with id $agentId" }
                        val agentEntry = agentManager.entryOrNull(agentId)
                        val (statusCode, response) = agentEntry?.run {
                            val plugin: Plugin? = this@PluginDispatcher.plugins[pluginId]
                            if (plugin != null) {
                                if (info.agentStatus == REGISTERED) {
                                    this[pluginId]?.let { adminPart ->
                                        val result = adminPart.doRawAction(action, inputStream)
                                        val statusResponse = result.toStatusResponse()
                                        HttpStatusCode.fromValue(statusResponse.code) to statusResponse
                                    } ?: (HttpStatusCode.BadRequest to ErrorResponse(
                                        "Cannot process data: plugin $pluginId not initialized for agent $agentId."
                                    ))
                                } else HttpStatusCode.BadRequest to ErrorResponse(
                                    "Cannot dispatch action for plugin '$pluginId', agent '$agentId' is not registered."
                                )
                            } else HttpStatusCode.NotFound to ErrorResponse("Plugin with id $pluginId not found")
                        } ?: (HttpStatusCode.NotFound to ErrorResponse("Agent with id $pluginId not found"))
                        logger.info { "response for '$agentId': $response" }
                        sendResponse(response, statusCode)
                    }

                    post<ApiRoot.AgentGroup.Plugin.DispatchAction, String>(
                        "Dispatch defined plugin actions in defined group"
                            .examples(
                                example("action", "some action name")
                            )
                            .responds(
                                ok<String>(
                                    example("")
                                ), notFound()
                            )
                    ) { pluginParent, action ->
                        val pluginId = pluginParent.pluginId
                        val groupId = pluginParent.parent.parent.groupId
                        val agents = agentManager.agentsByGroup(groupId)
                        logger.debug { "Dispatch action plugin with id $pluginId for agents with groupId $groupId" }
                        val (statusCode, response) = plugins[pluginId]?.let {
                            processMultipleActions(
                                agents,
                                pluginId,
                                action
                            )
                        } ?: (HttpStatusCode.NotFound to ErrorResponse("Plugin $pluginId not found."))
                        logger.trace { "response for '$groupId': $response" }
                        call.respond(statusCode, response)
                    }


                    //todo remove it cause it is duplicated in another place. EPMDJ-6145
                    get<ApiRoot.Agents.PluginData> { (_, agentId, pluginId, dataType) ->
                        logger.debug { "Get plugin data, agentId=$agentId, pluginId=$pluginId, dataType=$dataType" }
                        val dp: Plugin? = plugins[pluginId]
                        val agentInfo = agentManager[agentId]
                        val agentEntry = agentManager.entryOrNull(agentId)
                        val (statusCode: HttpStatusCode, response: Any) = when {
                            (dp == null) -> HttpStatusCode.NotFound to ErrorResponse("Plugin '$pluginId' not found")
                            (agentInfo == null) -> HttpStatusCode.NotFound to ErrorResponse("Agent '$agentId' not found")
                            (agentEntry == null) -> HttpStatusCode.NotFound to ErrorResponse("Data for agent '$agentId' not found")
                            else -> AgentSubscription(agentId, agentInfo.build.version).let { subscription ->
                                pluginCache.retrieveMessage(
                                    pluginId,
                                    subscription,
                                    "/data/$dataType"
                                ).toStatusResponsePair()
                            }
                        }
                        sendResponse(response, statusCode)
                    }

                    post<ApiRoot.Agents.TogglePlugin>(
                        "Toggle Plugin"
                            .responds(
                                ok<Unit>(), notFound()
                            )
                    ) { params ->
                        val (_, agentId, pluginId) = params
                        logger.debug { "Toggle plugin with id $pluginId for agent with id $agentId" }
                        val dp: Plugin? = plugins[pluginId]
                        val session = buildManager.agentSessions(agentId)
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

                    delete<ApiRoot.Agents.PluginBuild> { (_, agentId, pluginId, buildVersion) ->
                        logger.debug { "Starting to remove a build '$buildVersion' for agent '$agentId', plugin '$pluginId'..." }
                        val (status, msg) = if (agentId in agentManager) {
                            if (plugins[pluginId] != null) {
                                val curBuildVersion = agentManager.buildVersionByAgentId(agentId)
                                if (buildVersion != curBuildVersion) {
                                    pluginStoresDSM(pluginId).deleteBy<Stored> {
                                        (Stored::id.startsWith(agentKeyPattern(agentId, buildVersion)))
                                    }
                                    adminStore.deleteById<AgentBuildData>(AgentBuildId(agentId, buildVersion))
                                    buildManager.buildData(agentId).run {
                                        agentBuildManager.delete(buildVersion)
                                    }
                                    (cacheService as? MapDBCacheService)?.clear(
                                        AgentCacheKey(pluginId, agentId),
                                        buildVersion
                                    )
                                    HttpStatusCode.OK to EmptyContent
                                } else HttpStatusCode.BadRequest to ErrorResponse("Can not remove a current build")
                            } else HttpStatusCode.BadRequest to ErrorResponse("Plugin '$pluginId' not found")
                        } else HttpStatusCode.BadRequest to ErrorResponse("Agent '$agentId' not found")
                        call.respond(status, msg)
                    }

                    get<ApiRoot.Agents.PluginBuildsSummary> { (_, agentId, pluginId) ->
                        logger.debug { "Get builds summary, agentId=$agentId, pluginId=$pluginId" }
                        val (status, message) = agentManager[agentId]?.let {
                            val buildsSummary =
                                buildManager.buildData(agentId).agentBuildManager.agentBuilds.map { agentBuild ->
                                    val buildVersion = agentBuild.info.version
                                    BuildSummaryDto(
                                        buildVersion = buildVersion,
                                        detectedAt = agentBuild.detectedAt,
                                        summary = pluginCache.retrieveMessage(
                                            pluginId = pluginId,
                                            subscription = AgentSubscription(
                                                agentId = agentId,
                                                buildVersion = buildVersion
                                            ),
                                            destination = "/build/summary"
                                        ).toJson()
                                    )
                                }
                            HttpStatusCode.OK to buildsSummary
                        } ?: (HttpStatusCode.NotFound to ErrorResponse("Agent with id $agentId not found"))
                        call.respond(status, message)
                    }
                }
            }
        }
    }

    private fun pluginRoutes(pluginId: String): List<String> {
        runCatching {
            return findPluginRoutes(Routes::class)
        }
        logger.warn { "Cannot find routes for plugin '$pluginId'" }
        return emptyList()
    }

    private fun findPluginRoutes(
        kClassRoute: KClass<out Any>,
        pluginRoutes: List<String> = listOf(),
        parentPath: String = "",
    ): List<String> {
        return kClassRoute.nestedClasses.mapNotNull { kClass: KClass<*> ->
            locationRouteService.findRoute(kClass)?.let { path ->
                val newPath = parentPath + path
                val childRoutes = if (kClass.nestedClasses.any()) {
                    findPluginRoutes(kClass, pluginRoutes, newPath)
                } else emptyList()
                childRoutes.plus(newPath)
            }
        }.flatten()
    }

    private fun Routing.createPluginGetRoute(destination: String) {
        get("/api/plugins/{pluginId}$destination") {
            val pluginId = call.parameters["pluginId"] ?: ""
            val destinationWithValue = destination.replace(Regex("\\{[A-Za-z]*}")) {
                call.parameters[it.value.removePrefix("{").removeSuffix("}")] ?: ""
            }
            val (statusCode, response) = if (pluginId in plugins) {
                call.request.queryParameters.subscription(pluginId)?.let {
                    pluginCache.retrieveMessage(pluginId, it, destinationWithValue).toStatusResponsePair()
                } ?: (HttpStatusCode.NotFound to ErrorResponse("not found subscription"))
            } else HttpStatusCode.NotFound to ErrorResponse("plugin '$pluginId' not found")
            logger.debug { "for destination $destinationWithValue response: $response" }
            call.respond(statusCode, response)
        }
    }

    private fun Parameters.subscription(pluginId: String): Subscription? {
        val type = this["type"]
        val groupId = this["groupId"] ?: ""
        val agentId = this["agentId"] ?: ""
        val buildVersion = this["buildVersion"]
        logger.debug { "plugin $pluginId type $type id $agentId $groupId buildVersion $buildVersion" }
        return when (type) {
            "AGENT" -> AgentSubscription(agentId = agentId, buildVersion = buildVersion)
            "GROUP" -> GroupSubscription(groupId = groupId)
            else -> null
        }
    }

    private suspend fun PipelineContext<Unit, ApplicationCall>.sendResponse(
        response: Any,
        statusCode: HttpStatusCode,
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

        is FileResponse -> call.respondFile(
            file = response.data
        ).also {
            if (!response.data.delete())
                logger.warn { "File ${response.data} is not deleted" }
        }

        else -> call.respond(
            status = statusCode,
            message = response
        )
    }

    private suspend fun processMultipleActions(
        agents: List<Agent>,
        pluginId: String,
        action: String,
    ): Pair<HttpStatusCode, List<JsonElement>> {
        val statusesResponse: List<JsonElement> = supervisorScope {
            agents.map { agent ->
                val agentId = agent.info.id
                val agentStatus = agent.info.agentStatus
                async {
                    val status = buildManager.buildStatus(agentId)
                    if (agentStatus == REGISTERED) {
                        agent[pluginId]?.run {
                            val adminActionResult = processAction(action, buildManager::agentSessions)
                            adminActionResult.toStatusResponse()
                        }
                    } else if (agentStatus == NOT_REGISTERED || status == OFFLINE) {
                        null
                    } else {
                        "Agent $agentId is in the wrong state: Agent status: '$agentStatus', build status '$status'".run {
                            StatusMessageResponse(
                                code = HttpStatusCode.Conflict.value,
                                message = this
                            )
                        }
                    }
                }
            }.mapNotNull { deferred ->
                runCatching { deferred.await() }.getOrNull()
            }.map { WithStatusCode.serializer() toJson it }
        }
        return HttpStatusCode.OK to statusesResponse
    }
}

fun Routing.pluginDispatcherRoutes() {
    val pluginDispatcher by closestDI().instance<PluginDispatcher>()
    pluginDispatcher.initRouting(this)
}

private fun Any.toStatusResponse(): WithStatusCode = when (this) {
    is ActionResult -> when (val d = data) {
        is String -> StatusMessageResponse(code, d)
        is File -> FileResponse(code, d)
        else -> StatusResponse(code, d.actionToJson())
    }

    else -> StatusResponse(HttpStatusCode.OK.value, actionToJson())
}

private fun Any.actionToJson(): JsonElement = actionSerializerOrNull()!! toJson this
