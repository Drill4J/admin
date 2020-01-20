package com.epam.drill.admin.endpoints.plugin

import com.epam.drill.admin.common.*
import com.epam.drill.api.*
import com.epam.drill.common.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.plugin.api.end.*
import com.epam.drill.plugin.api.message.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.admin.router.*
import com.epam.drill.admin.service.*
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
    private val app: Application by instance()
    private val plugins: Plugins by instance()
    private val agentManager: AgentManager by instance()
    private val topicResolver: TopicResolver by instance()
    private val logger = KotlinLogging.logger {}

    suspend fun processPluginData(pluginData: String, agentInfo: AgentInfo) {
        val message = MessageWrapper.serializer().parse(pluginData)
        val pluginId = message.pluginId
        try {
            val dp: Plugin = plugins[pluginId] ?: return
            val pluginClass = dp.pluginClass
            val agentEntry = agentManager.full(agentInfo.id)
            val plugin: AdminPluginPart<*> = agentManager.instantiateAdminPluginPart(agentEntry, pluginClass, pluginId)
            plugin.processData(message.drillMessage)
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
                        HttpStatusCode.OK to ""
                    } else {
                        logger.warn {
                            "AgentInfo with associated with id $agentId" +
                                    " or plugin configuration associated with id $pluginId was not found"
                        }
                        HttpStatusCode.NotFound to ""
                    }
                    call.respondJsonIfErrorsOccured(statusCode, response)
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
                        HttpStatusCode.NotFound to "Plugin with id $pluginId not found"
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
                    call.respondJsonIfErrorsOccured(statusCode, response.toString())
                }
            }

            authenticate {
                val dispatchPluginsResponds = "Dispatch defined plugin actions in defined service group"
                    .responds()
                post<Routes.Api.ServiceGroup.DispatchPluginAction>(dispatchPluginsResponds) { params ->
                    val (serviceGroupId, pluginId) = params
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



            get<Routes.Api.Agent.GetPluginData> { (agentId, pluginId) ->
                logger.debug { "Get data plugin with id $pluginId for agent with id $agentId" }
                val dp: Plugin? = plugins[pluginId]
                val agentInfo = agentManager[agentId]
                val (statusCode, response) = when {
                    (dp == null) -> HttpStatusCode.NotFound to "plugin with id $pluginId not found"
                    (agentInfo == null) -> HttpStatusCode.NotFound to "agent with id $agentId not found"
                    else -> {
                        val agentEntry = agentManager.full(agentId)
                        val adminPart: AdminPluginPart<*> =
                            agentManager.instantiateAdminPluginPart(agentEntry, dp.pluginClass, pluginId)
                        val response = adminPart.getPluginData(context.parameters.asMap())
                        HttpStatusCode.OK to response
                    }
                }
                logger.debug { response }
                when (response) {
                    is ByteArray -> call.respondBytes(response, ContentType.MultiPart.Any, HttpStatusCode.OK)
                    "" -> call.respond(HttpStatusCode.BadRequest, "no data")
                    else -> call.respondJsonIfErrorsOccured(statusCode, response.toString())
                }

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
                                    HttpStatusCode.BadRequest to "Plugin '${pluginIdObject.pluginId}' is already in agent '$agentId'"
                                } else {
                                    agentManager.apply {
                                        addPlugins(agentInfo, listOf(pluginIdObject.pluginId))
                                        sendPluginsToAgent(agentInfo)
                                        agentInfo.sync(true)
                                    }
                                    HttpStatusCode.OK to "Plugin '${pluginIdObject.pluginId}' was added to agent '$agentId'"
                                }
                            } else {
                                HttpStatusCode.BadRequest to "Agent '$agentId' not found"
                            }
                        }
                        else -> HttpStatusCode.BadRequest to "Plugin ${pluginIdObject.pluginId} not found."
                    }
                    logger.debug { msg }
                    call.respondJsonIfErrorsOccured(status, msg)
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
                        (dp == null) -> HttpStatusCode.NotFound to "plugin with id $pluginId not found"
                        (session == null) -> HttpStatusCode.NotFound to "agent with id $agentId not found"
                        else -> {
                            session.sendToTopic<Communication.Plugin.ToggleEvent>(TogglePayload(pluginId))
                            HttpStatusCode.OK to "OK"
                        }
                    }
                    logger.debug { response }
                    call.respondJsonIfErrorsOccured(statusCode, response)
                }
            }

        }
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
            HttpStatusCode.NotFound to "Plugin with id $pluginId not found"
        } else {

            pluginDispatcher.processMultipleActions(
                agents,
                dp,
                pluginId,
                action
            )
        }
        logger.info { "$response" }
        pipelineContext.call.respondJsonIfErrorsOccured(statusCode, response.toString())
    }

    private suspend fun processMultipleActions(
        agents: List<AgentEntry>,
        dp: Plugin,
        pluginId: String,
        action: String
    ): Pair<HttpStatusCode, Any> {
        val sessionId = UUID.randomUUID().toString()
        return agents.map { agentEntry ->
            val adminPart: AdminPluginPart<*> =
                agentManager.instantiateAdminPluginPart(agentEntry, dp.pluginClass, pluginId)
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
                    is String -> adminActionResult
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
        val adminPart: AdminPluginPart<*> =
            agentManager.instantiateAdminPluginPart(agentEntry, dp.pluginClass, pluginId)
        val adminActionResult = adminPart.doRawAction(action)
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
                is String -> adminActionResult
                else -> EmptyContent
            }
        }

    }
}
