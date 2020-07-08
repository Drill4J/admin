package com.epam.drill.admin.endpoints.plugin

import com.epam.drill.admin.api.routes.*
import com.epam.drill.admin.common.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.admin.plugin.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.admin.router.*
import com.epam.drill.api.*
import com.epam.drill.common.*
import com.epam.drill.plugin.api.end.*
import com.epam.drill.plugin.api.message.*
import de.nielsfalk.ktor.swagger.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.json.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*
import java.util.*

class PluginDispatcher(override val kodein: Kodein) : KodeinAware {
    private val app by instance<Application>()
    private val plugins by instance<Plugins>()
    private val pluginCache by instance<PluginCache>()
    private val agentManager by instance<AgentManager>()
    private val topicResolver by instance<TopicResolver>()
    private val logger = KotlinLogging.logger {}

    suspend fun processPluginData(pluginData: String, agentInfo: AgentInfo) {
        val message = MessageWrapper.serializer().parse(pluginData)
        val pluginId = message.pluginId
        plugins[pluginId]?.let { plugin: Plugin ->
            val agentEntry = agentManager.full(agentInfo.id)!!
            val pluginInstance: AdminPluginPart<*> = agentManager.ensurePluginInstance(agentEntry, plugin)
            pluginInstance.processData(message.drillMessage)

        } ?: logger.error { "Plugin $pluginId not loaded!" }
    }


    init {
        app.routing {
            authenticate {
                patch<ApiRoot.Agents.Plugin> { (_, agentId, pluginId) ->
                    logger.debug { "Update plugin with id $pluginId for agent with id $agentId" }
                    val config = call.receive<String>()
                    val pc = PluginConfig(pluginId, config)
                    logger.debug { "Plugin config $config" }
                    agentManager.agentSession(agentId)?.sendToTopic<Communication.Plugin.UpdateConfigEvent>(pc)
                    val (statusCode, response) = if (agentManager.updateAgentPluginConfig(agentId, pc)) {
                        topicResolver.sendToAllSubscribed(WsRoutes.AgentPluginConfig(agentId, pluginId))
                        logger.debug { "Plugin with id $pluginId for agent with id $agentId was updated" }
                        HttpStatusCode.OK to EmptyContent
                    } else {
                        val errorMessage = "AgentInfo with associated with id $agentId" +
                            " or plugin configuration associated with id $pluginId was not found"
                        logger.warn { errorMessage }
                        HttpStatusCode.NotFound to ErrorResponse(errorMessage)
                    }
                    call.respond(statusCode, response)
                }
            }
            authenticate {
                val dispatchResponds = "Dispatch Plugin Action"
                    .examples(
                        example("action", "some action name")
                    )
                    .responds(
                        ok<String>(
                            example("")
                        ), notFound()
                    )
                post<ApiRoot.Agents.DispatchPluginAction, String>(dispatchResponds) { payload, action ->
                    val (_, agentId, pluginId) = payload
                    logger.debug { "Dispatch action plugin with id $pluginId for agent with id $agentId" }
                    val agentEntry = agentManager.full(agentId)
                    val (statusCode, response) = agentEntry?.run {
                        val plugin: Plugin? = this@PluginDispatcher.plugins[pluginId]
                        if (plugin != null) {
                            if (agentEntry.agent.status == AgentStatus.ONLINE) {
                                val adminPart: AdminPluginPart<*> = agentManager.ensurePluginInstance(this, plugin)
                                val result = adminPart.processSingleAction(action)
                                val statusResponse = result.toStatusResponse()
                                HttpStatusCode.fromValue(statusResponse.code) to statusResponse
                            } else HttpStatusCode.BadRequest to ErrorResponse(
                                message = "Cannot dispatch action for plugin '$pluginId', agent '$agentId' is not online."
                            )
                        } else HttpStatusCode.NotFound to ErrorResponse("Plugin with id $pluginId not found")
                    } ?: HttpStatusCode.NotFound to ErrorResponse("Agent with id $pluginId not found")
                    logger.info { "$response" }
                    call.respond(statusCode, response)
                }
            }

            authenticate {
                val dispatchPluginsResponds = "Dispatch defined plugin actions in defined service group"
                    .examples(
                        example("action", "some action name")
                    )
                    .responds(
                        ok<String>(
                            example("")
                        ), notFound()
                    )
                post<ApiRoot.ServiceGroup.Plugin.DispatchAction, String>(dispatchPluginsResponds) { pluginParent, action ->
                    val pluginId = pluginParent.parent.pluginId
                    val serviceGroupId = pluginParent.parent.parent.serviceGroupId
                    val agents = agentManager.serviceGroup(serviceGroupId)
                    logger.debug { "Dispatch action plugin with id $pluginId for agents with serviceGroupId $serviceGroupId" }
                    val plugin: Plugin? = plugins[pluginId]
                    val (statusCode, response) = if (plugin == null)
                        HttpStatusCode.NotFound to ErrorResponse("Plugin with id $pluginId not found")
                    else
                        processMultipleActions(
                            agents,
                            pluginId,
                            action
                        )
                    logger.info { "$response" }
                    call.respond(statusCode, response)
                }
            }

            get<ApiRoot.Agents.PluginData> { (_, agentId, pluginId, dataType) ->
                logger.debug { "Get plugin data, agentId=$agentId, pluginId=$pluginId, dataType=$dataType" }
                val dp: Plugin? = plugins[pluginId]
                val agentInfo = agentManager[agentId]
                val agentEntry = agentManager.full(agentId)
                val (statusCode: HttpStatusCode, response: Any) = when {
                    (dp == null) -> HttpStatusCode.NotFound to ErrorResponse("Plugin '$pluginId' not found")
                    (agentInfo == null) -> HttpStatusCode.NotFound to ErrorResponse("Agent '$agentId' not found")
                    (agentEntry == null) -> HttpStatusCode.NotFound to ErrorResponse("Data for agent '$agentId' not found")
                    else -> AgentSubscription(agentId, agentInfo.buildVersion).let { subscription ->
                        val key = subscription.toKey("/data/$dataType")
                        pluginCache[key].toStatusResponsePair()
                    }
                }
                sendResponse(response, statusCode)
            }

            authenticate {
                val addNewPluginResponds = "Add new plugin"
                    .examples(
                        example("pluginId", PluginId("some plugin id"))
                    )
                    .responds(
                        ok<String>(
                            example("result", "Plugin was added")
                        ), badRequest()
                    )
                post<ApiRoot.Agents.Plugins, PluginId>(addNewPluginResponds) { params, pluginIdObject ->
                    val (_, agentId) = params
                    logger.debug { "Add new plugin for agent with id $agentId" }
                    val (status, msg) = when (pluginIdObject.pluginId) {
                        in plugins.keys -> {
                            if (agentId in agentManager) {
                                val agentInfo = agentManager[agentId]!!
                                if (agentInfo.plugins.any { it.id == pluginIdObject.pluginId }) {
                                    HttpStatusCode.BadRequest to
                                        ErrorResponse("Plugin '${pluginIdObject.pluginId}' is already in agent '$agentId'")
                                } else {
                                    agentManager.apply {
                                        agentInfo.addPlugins(listOf(pluginIdObject.pluginId))
                                        sendPluginsToAgent(agentInfo)
                                        agentInfo.sync(true)
                                    }
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
                val togglePluginResponds = "Toggle Plugin"
                    .responds(
                        ok<String>(
                            example(
                                "result",
                                WsSendMessage(
                                    type = WsMessageType.MESSAGE,
                                    destination = "some destination",
                                    message = "some message"
                                )
                            )
                        ), notFound()
                    )
                post<ApiRoot.Agents.TogglePlugin>(togglePluginResponds) { params ->
                    val (_, agentId, pluginId) = params
                    logger.debug { "Toggle plugin with id $pluginId for agent with id $agentId" }
                    val dp: Plugin? = plugins[pluginId]
                    val session = agentManager.agentSession(agentId)
                    val (statusCode, response) = when {
                        (dp == null) -> HttpStatusCode.NotFound to ErrorResponse("plugin with id $pluginId not found")
                        (session == null) -> HttpStatusCode.NotFound to ErrorResponse("agent with id $agentId not found")
                        else -> {
                            session.sendToTopic<Communication.Plugin.ToggleEvent>(TogglePayload(pluginId))
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
    ): Pair<HttpStatusCode, Any> {
        val sessionId = UUID.randomUUID().toString()
        val statusesResponse: List<WithStatusCode> = agents.mapNotNull { entry: AgentEntry ->
            when (entry.agent.status) {
                AgentStatus.ONLINE -> entry[pluginId]?.run {
                    val sessionAction = sessionSubstituting(action, sessionId)
                    val adminActionResult = processSingleAction(sessionAction)
                    adminActionResult.toStatusResponse()
                }
                AgentStatus.NOT_REGISTERED, AgentStatus.OFFLINE -> null
                else -> "Agent ${entry.agent.id} is in the wrong state - ${entry.agent.status}".run {
                    statusMessageResponse(HttpStatusCode.Conflict.value)
                }
            }
        }
        return HttpStatusCode.OK to statusesResponse
    }

    private fun sessionSubstituting(action: String, sessionId: String): String {
        val parseJson = json.parseJson(action) as? JsonObject ?: return action
        if (parseJson["type"]?.contentOrNull != "START") return action
        val mainContainer = parseJson["payload"] as? JsonObject ?: return action
        val sessionIdContainer = mainContainer["sessionId"]
        return if (sessionIdContainer == null || sessionIdContainer.content.isEmpty()) {
            (mainContainer.content as MutableMap<String, JsonElement>)["sessionId"] =
                JsonElement.serializer() parse sessionId
            parseJson.toString()
        } else action
    }

    private suspend fun AdminPluginPart<*>.processSingleAction(action: String): Any {
        val result = doRawAction(action)
        when (result) {
            is StatusMessage -> null
            Unit -> action
            else -> stringifyAction(result)
        }?.let { agentPartMsg ->
            agentManager.agentSession(agentInfo.id)?.apply {
                val agentAction = PluginAction(id, agentPartMsg)
                sendToTopic<Communication.Plugin.DispatchEvent>(agentAction)
            }
        }
        return result
    }
}

private fun Any.toStatusResponse(): WithStatusCode = when (this) {
    is StatusMessage -> message.statusMessageResponse(code)
    is String -> StatusResponse(HttpStatusCode.OK.value, this)
    Unit -> StatusResponse(HttpStatusCode.OK.value, JsonNull)
    else -> StatusResponse(HttpStatusCode.OK.value, this)
}
