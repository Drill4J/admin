package com.epam.drill.admin.endpoints.plugin

import com.epam.drill.admin.common.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.admin.router.*
import com.epam.drill.admin.service.*
import com.epam.drill.admin.servicegroup.*
import com.epam.drill.api.*
import com.epam.drill.common.*
import com.epam.drill.plugin.api.end.*
import com.epam.drill.plugin.api.message.*
import de.nielsfalk.ktor.swagger.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
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
    private val app: Application by instance()
    private val plugins: Plugins by instance()
    private val agentManager: AgentManager by instance()
    private val topicResolver: TopicResolver by instance()
    private val logger = KotlinLogging.logger {}

    suspend fun processPluginData(pluginData: String, agentInfo: AgentInfo) {
        val message = MessageWrapper.serializer().parse(pluginData)
        val pluginId = message.pluginId
        try {
            val plugin: Plugin = plugins[pluginId] ?: return
            val agentEntry = agentManager.full(agentInfo.id)!!
            val pluginInstance: AdminPluginPart<*> = agentManager.ensurePluginInstance(agentEntry, plugin)
            pluginInstance.processData(message.drillMessage)
        } catch (ee: Exception) {
            logger.error(ee) { "Processing plugin data was finished with exception" }
        }
    }


    init {
        app.routing {
            authenticate {
                patch<Routes.Api.Agent.UpdatePlugin> { (agentId, pluginId) ->
                    logger.debug { "Update plugin with id $pluginId for agent with id $agentId" }
                    val config = call.receive<String>()
                    val pc = PluginConfig(pluginId, config)
                    logger.debug { "Plugin config $config" }
                    agentManager.agentSession(agentId)?.sendToTopic<Communication.Plugin.UpdateConfigEvent>(pc)
                    val (statusCode, response) = if (agentManager.updateAgentPluginConfig(agentId, pc)) {
                        topicResolver.sendToAllSubscribed("/$agentId/$pluginId/config")
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
                post<Routes.Api.Agent.DispatchPluginAction, String>(dispatchResponds) { payload, action ->
                    val (id, pluginId) = payload
                    logger.debug { "Dispatch action plugin with id $pluginId for agent with id $id" }
                    val dp: Plugin? = plugins[pluginId]
                    val (statusCode, response) = if (dp == null) {
                        HttpStatusCode.NotFound to ErrorResponse("Plugin with id $pluginId not found")
                    } else {
                        call.attributes.getOrNull(srv)?.run {
                            processMultipleActions(
                                agentManager.agentStorage.values.filter { it.agent.serviceGroup == id },
                                dp,
                                pluginId,
                                action
                            )
                        } ?: processSingleAction(dp, id, action)

                    }
                    logger.info { "$response" }
                    call.respond(statusCode, response)
                }
            }

            authenticate {
                val dispatchPluginsResponds = "Dispatch defined plugin actions in defined service group"
                    .responds()
                post<Routes.Api.ServiceGroup.Plugin.DispatchAction>(dispatchPluginsResponds) { (pluginParent) ->
                    val pluginId = pluginParent.pluginId
                    val serviceGroupId = pluginParent.serviceGroupParent.serviceGroupId
                    val agents = agentManager.serviceGroup(serviceGroupId)
                    logger.debug { "Dispatch action plugin with id $pluginId for agents with serviceGroupId $serviceGroupId" }
                    dispatchPluginAction(pluginId, this, this@PluginDispatcher, agents)
                }
            }

            authenticate {
                val dispatchResponds = "Dispatch all plugin action"
                    .responds()
                post<Routes.Api.DispatchAllPluginAction>(dispatchResponds) { params ->
                    val (pluginId) = params
                    val agents = agentManager.getAllAgents()
                    dispatchPluginAction(pluginId, this, this@PluginDispatcher, agents.toList())
                }
            }



            get<Routes.Api.Agent.PluginData> { (agentId, pluginId, dataType) ->
                logger.debug { "Get plugin data, agentId=$agentId, pluginId=$pluginId, dataType=$dataType" }
                val dp: Plugin? = plugins[pluginId]
                val agentInfo = agentManager[agentId]
                val agentEntry = agentManager.full(agentId)
                val (statusCode: HttpStatusCode, response: Any) = when {
                    (dp == null) -> HttpStatusCode.NotFound to ErrorResponse("plugin '$pluginId' not found")
                    (agentInfo == null) -> HttpStatusCode.NotFound to ErrorResponse("agent '$agentId' not found")
                    (agentEntry == null) -> HttpStatusCode.NotFound to ErrorResponse("data for agent '$agentId' not found")
                    else -> {
                        val adminPart: AdminPluginPart<*> = agentManager.ensurePluginInstance(agentEntry, dp)
                        val response = adminPart.getPluginData(mapOf("type" to dataType))
                        HttpStatusCode.OK to response
                    }
                }
                logger.debug { response }
                sendResponse(response, statusCode)
            }

            get<Routes.Api.ServiceGroup.Plugin.Data> { (pluginParent, dataType) ->
                val pluginId = pluginParent.pluginId
                val serviceGroupId = pluginParent.serviceGroupParent.serviceGroupId
                logger.debug { "Get plugin data, serviceGroupId=${serviceGroupId}serviceGroupId, pluginId=${pluginId}, dataType=$dataType" }
                val dp: Plugin? = plugins[pluginId]
                val serviceGroup: List<AgentEntry> = agentManager.serviceGroup(serviceGroupId)

                val (statusCode: HttpStatusCode, response: Any) = when {
                    (dp == null) -> HttpStatusCode.NotFound to ErrorResponse("plugin '$pluginId' not found")
                    (serviceGroup.isEmpty()) -> HttpStatusCode.NotFound to ErrorResponse("data for serviceGroup '$serviceGroupId' not found")
                    else -> {
                        val response = serviceGroup.map {
                            val adminPart = agentManager.ensurePluginInstance(it, dp)
                            adminPart.getPluginData(mapOf("type" to dataType))
                        }.aggregate() ?: ""
                        HttpStatusCode.OK to response
                    }
                }
                logger.debug { response }
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
                post<Routes.Api.Agent.AddNewPlugin, PluginId>(addNewPluginResponds) { params, pluginIdObject ->
                    val (agentId) = params
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
                                        addPlugins(agentInfo, listOf(pluginIdObject.pluginId))
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
                            example("result",
                                WsSendMessage(
                                    WsMessageType.MESSAGE,
                                    "some destination",
                                    "some message"
                                )
                            )
                        ), notFound()
                    )
                post<Routes.Api.Agent.TogglePlugin>(togglePluginResponds) { params ->
                    val (agentId, pluginId) = params
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
        is ByteArray -> call.respondBytes(response, ContentType.MultiPart.Any, HttpStatusCode.OK)
        "" -> call.respond(HttpStatusCode.BadRequest, "no data")
        is String -> call.respondText(
            response,
            ContentType.Application.Json
        )
        else -> call.respond(
            statusCode,
            response
        )
    }

    private suspend fun dispatchPluginAction(
        pluginId: String,
        pipelineContext: PipelineContext<Unit, ApplicationCall>,
        pluginDispatcher: PluginDispatcher,
        agents: List<AgentEntry>
    ) {
        val action = pipelineContext.call.receive<String>()
        val dp: Plugin? = plugins[pluginId]
        val (statusCode, response) = if (dp == null) {
            HttpStatusCode.NotFound to ErrorResponse("Plugin with id $pluginId not found")
        } else {

            pluginDispatcher.processMultipleActions(
                agents,
                dp,
                pluginId,
                action
            )
        }
        logger.info { "$response" }
        pipelineContext.call.respond(statusCode, response)
    }

    private suspend fun processMultipleActions(
        agents: List<AgentEntry>,
        dp: Plugin,
        pluginId: String,
        action: String
    ): Pair<HttpStatusCode, Any> {
        val sessionId = UUID.randomUUID().toString()
        return agents.map { agentEntry ->
            val adminPart: AdminPluginPart<*> = agentManager.ensurePluginInstance(agentEntry, dp)
            val adminActionResult = adminPart.doRawAction(sessionSubstituting(action, sessionId))
            val agentPartMsg = when (adminActionResult) {
                is String -> adminActionResult
                is Unit -> action
                else -> Unit
            }
            if (agentPartMsg is String) {
                agentEntry.agentSession.apply {
                    val agentAction = PluginAction(pluginId, agentPartMsg)
                    val agentPluginMsg = PluginAction.serializer() stringify agentAction
                    sendToTopic<Communication.Plugin.DispatchEvent>(agentPluginMsg)
                }
            }
            if (adminActionResult is StatusMessage) {
                HttpStatusCode.fromValue(adminActionResult.code) to adminActionResult.message
            } else {
                HttpStatusCode.OK to when (adminActionResult) {
                    is String -> TextContent(
                        text = adminActionResult,
                        contentType = ContentType.Application.Json
                    )
                    else -> EmptyContent
                }
            }
        }.reduce { k, _ -> k }
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

    private suspend fun processSingleAction(
        dp: Plugin,
        id: String,
        action: String
    ): Pair<HttpStatusCode, Any> = run {
        val pluginId = dp.pluginBean.id
        val agentEntry = agentManager.full(id)
        val adminActionResult: Any = if (agentEntry != null) {
            val adminPart: AdminPluginPart<*> = agentManager.ensurePluginInstance(agentEntry, dp)
            adminPart.doRawAction(action)
        } else Unit
        val agentPartMsg = when (adminActionResult) {
            is String -> adminActionResult
            is Unit -> action
            else -> Unit
        }
        if (agentPartMsg is String) {
            agentManager.agentSession(id)?.apply {
                val agentAction = PluginAction(pluginId, agentPartMsg)
                val agentPluginMsg = PluginAction.serializer() stringify agentAction
                sendToTopic<Communication.Plugin.DispatchEvent>(agentPluginMsg)
            }
        }
        if (adminActionResult is StatusMessage) {
            HttpStatusCode.fromValue(adminActionResult.code) to adminActionResult.message
        } else {
            HttpStatusCode.OK to when (adminActionResult) {
                is String -> TextContent(
                    text = adminActionResult,
                    contentType = ContentType.Application.Json
                )
                else -> EmptyContent
            }
        }

    }
}
