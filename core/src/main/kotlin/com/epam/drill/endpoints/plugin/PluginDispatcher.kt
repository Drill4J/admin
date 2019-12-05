package com.epam.drill.endpoints.plugin

import com.epam.drill.common.*
import com.epam.drill.endpoints.*
import com.epam.drill.endpoints.agent.*
import com.epam.drill.plugin.api.end.*
import com.epam.drill.plugin.api.message.*
import com.epam.drill.plugins.*
import com.epam.drill.router.*
import com.epam.drill.service.*
import com.epam.drill.util.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.routing.*
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
                    agentManager.agentSession(agentId)
                        ?.send(PluginConfig.serializer().agentWsMessage("/plugins/updatePluginConfig", pc))

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
                post<Routes.Api.Agent.DispatchPluginAction> { (id, pluginId) ->
                    logger.debug { "Dispatch action plugin with id $pluginId for agent with id $id" }
                    val action = call.receive<String>()
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


            get<Routes.Api.Agent.GetPluginData> { (agentId, pluginId) ->
                logger.debug { "Get data plugin with id $pluginId for agent with id $agentId" }

                val params = call.parameters.asMap()
                val dp: Plugin? = plugins[pluginId]
                val agentInfo = agentManager[agentId]
                val (statusCode, response) = when {
                    (dp == null) -> HttpStatusCode.NotFound to "plugin with id $pluginId not found"
                    (agentInfo == null) -> HttpStatusCode.NotFound to "agent with id $agentId not found"
                    else -> {
                        val agentEntry = agentManager.full(agentId)
                        val adminPart: AdminPluginPart<*> =
                            agentManager.instantiateAdminPluginPart(agentEntry, dp.pluginClass, pluginId)
                        val response = adminPart.getPluginData(params)
                        HttpStatusCode.OK to response
                    }
                }
                logger.debug { response }
                call.respondJsonIfErrorsOccured(statusCode, response)
            }

            authenticate {
                post<Routes.Api.Agent.AddNewPlugin> { (agentId) ->
                    logger.debug { "Add new plugin for agent with id $agentId" }
                    val pluginId = call.parse(PluginId.serializer()).pluginId
                    val (status, msg) = when (pluginId) {
                        in plugins.keys -> {
                            if (agentId in agentManager) {
                                val agentInfo = agentManager[agentId]!!
                                if (agentInfo.plugins.any { it.id == pluginId }) {
                                    HttpStatusCode.BadRequest to "Plugin '$pluginId' is already in agent '$agentId'"
                                } else {
                                    agentManager.addPlugins(agentInfo, listOf(pluginId))
                                    agentManager.sendPluginsToAgent(agentInfo)
                                    agentManager.sync(agentInfo, true)
                                    HttpStatusCode.OK to "Plugin '$pluginId' was added to agent '$agentId'"
                                }
                            } else {
                                HttpStatusCode.BadRequest to "Agent '$agentId' not found"
                            }
                        }
                        else -> HttpStatusCode.BadRequest to "Plugin $pluginId not found."
                    }
                    logger.debug { msg }
                    call.respondJsonIfErrorsOccured(status, msg)
                }
            }
            authenticate {
                post<Routes.Api.Agent.TogglePlugin> { (agentId, pluginId) ->
                    logger.debug { "Toggle plugin with id $pluginId for agent with id $agentId" }
                    val dp: Plugin? = plugins[pluginId]
                    val session = agentManager.agentSession(agentId)
                    val (statusCode, response) = when {
                        (dp == null) -> HttpStatusCode.NotFound to "plugin with id $pluginId not found"
                        (session == null) -> HttpStatusCode.NotFound to "agent with id $agentId not found"
                        else -> {
                            session.send(
                                Frame.Text(
                                    WsSendMessage.serializer() stringify
                                            WsSendMessage(
                                                WsMessageType.MESSAGE,
                                                "/plugins/togglePlugin",
                                                TogglePayload(pluginId)
                                            )
                                )
                            )
                            HttpStatusCode.OK to "OK"
                        }
                    }
                    logger.debug { response }
                    call.respondJsonIfErrorsOccured(statusCode, response)
                }
            }

        }
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
            val actionObject = adminPart.parseAction(action)
            val adminActionResult =
                if (actionObject is com.epam.drill.plugins.coverage.StartNewSession &&
                    actionObject.payload.sessionId.isEmpty()
                ) {
                    val reAction = actionObject.copy(actionObject.payload.copy(sessionId = sessionId))
                    @Suppress("UNCHECKED_CAST")
                    (adminPart as AdminPluginPart<Any>).doAction(reAction)
                } else adminPart.doRawAction(action)
            val agentPartMsg = when (adminActionResult) {
                is String -> adminActionResult
                is Unit -> action
                else -> Unit
            }
            if (agentPartMsg is String) {
                agentEntry.agentSession.apply {
                    val agentAction = PluginAction(pluginId, agentPartMsg)
                    val agentPluginMsg = PluginAction.serializer() stringify agentAction
                    val agentMsg =
                        Message(MessageType.MESSAGE, "/plugins/action", agentPluginMsg)
                    val agentFrame = Frame.Text(Message.serializer() stringify agentMsg)
                    send(agentFrame)
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
                val agentMsg =
                    Message(MessageType.MESSAGE, "/plugins/action", agentPluginMsg)
                val agentFrame = Frame.Text(Message.serializer() stringify agentMsg)
                send(agentFrame)
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