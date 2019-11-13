package com.epam.drill.endpoints.plugin

import com.epam.drill.common.*
import com.epam.drill.endpoints.*
import com.epam.drill.endpoints.agent.*
import com.epam.drill.plugin.api.*
import com.epam.drill.plugin.api.end.*
import com.epam.drill.plugin.api.message.*
import com.epam.drill.plugins.*
import com.epam.drill.router.*
import com.epam.drill.util.*
import com.epam.kodux.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import mu.*
import org.kodein.di.*
import org.kodein.di.generic.*
import kotlin.collections.set

class PluginDispatcher(override val kodein: Kodein) : KodeinAware {
    private val app: Application by instance()
    private val plugins: Plugins by instance()
    private val agentManager: AgentManager by instance()
    private val wsService: Sender by kodein.instance()
    private val topicResolver: TopicResolver by instance()
    private val store: StoreManager by instance()
    private val logger = KotlinLogging.logger {}

    suspend fun processPluginData(pluginData: String, agentInfo: AgentInfo) {
        val message = MessageWrapper.serializer().parse(pluginData)
        val pluginId = message.pluginId
        try {
            val dp: Plugin = plugins[pluginId] ?: return
            val pluginClass = dp.pluginClass
            val agentEntry = agentManager.full(agentInfo.id)
            val plugin: AdminPluginPart<*> = fillPluginInstance(agentEntry, pluginClass, pluginId)
            plugin.processData(message.drillMessage)
        } catch (ee: Exception) {
            logger.error(ee) { "Processing plugin data was finished with exception" }
        }
    }

    private suspend fun fillPluginInstance(
        agentEntry: AgentEntry?,
        pluginClass: Class<AdminPluginPart<*>>,
        pluginId: String
    ): AdminPluginPart<*> {
        return agentEntry?.instance!![pluginId] ?: run {
            val constructor =
                pluginClass.getConstructor(
                    AdminData::class.java,
                    Sender::class.java,
                    StoreClient::class.java,
                    AgentInfo::class.java,
                    String::class.java
                )
            val agentInfo = agentEntry.agent
            val plugin = constructor.newInstance(
                agentManager.adminData(agentInfo.id),
                wsService,
                store.agentStore(agentInfo.id),
                agentInfo,
                pluginId
            )
            plugin.initialize()
            agentEntry.instance[pluginId] = plugin
            plugin
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

                    if (agentManager.updateAgentPluginConfig(agentId, pc)) {
                        topicResolver.sendToAllSubscribed("/$agentId/$pluginId/config")
                        call.respond(HttpStatusCode.OK, "")
                        logger.debug { "Plugin with id $pluginId for agent with id $agentId was updated" }
                    } else {
                        call.respond(HttpStatusCode.NotFound, "")
                        logger.warn {
                            "AgentInfo with associated with id $agentId" +
                                    " or plugin configuration associated with id $pluginId was not found"
                        }
                    }
                }
            }
            authenticate {
                post<Routes.Api.Agent.DispatchPluginAction> { (agentId, pluginId) ->
                    logger.debug { "Dispatch action plugin with id $pluginId for agent with id $agentId" }
                    val action = call.receive<String>()
                    val dp: Plugin? = plugins[pluginId]
                    val agentInfo = agentManager[agentId]
                    val (statusCode, response) = when {
                        (dp == null) -> HttpStatusCode.NotFound to "plugin with id $pluginId not found"
                        (agentInfo == null) -> HttpStatusCode.NotFound to "agent with id $agentId not found"
                        else -> {
                            val agentEntry = agentManager.full(agentId)
                            val adminPart: AdminPluginPart<*> = fillPluginInstance(agentEntry, dp.pluginClass, pluginId)
                            val adminActionResult = adminPart.doRawAction(action)
                            val agentPartMsg = when (adminActionResult) {
                                is String -> adminActionResult
                                is Unit -> action
                                else -> Unit
                            }
                            if (agentPartMsg is String) {
                                agentManager.agentSession(agentId)?.apply {
                                    val agentAction = PluginAction(pluginId, agentPartMsg)
                                    val agentPluginMsg = PluginAction.serializer() stringify agentAction
                                    val agentMsg = Message(MessageType.MESSAGE, "/plugins/action", agentPluginMsg)
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
                    logger.info { "$response" }
                    call.respond(statusCode, response)
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
                        val adminPart: AdminPluginPart<*> = fillPluginInstance(agentEntry, dp.pluginClass, pluginId)
                        val response = adminPart.getPluginData(params)
                        HttpStatusCode.OK to response
                    }
                }
                logger.debug { response }
                call.respondText(
                    response,
                    ContentType.Application.Json,
                    statusCode
                )
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
                                    agentManager.addPluginFromLib(agentId, pluginId)
                                    val dp: Plugin = plugins[pluginId]!!
                                    val pluginClass = dp.pluginClass
                                    val agentEntry = agentManager.full(agentInfo.id)
                                    fillPluginInstance(agentEntry, pluginClass, pluginId)
                                    HttpStatusCode.OK to "Plugin '$pluginId' was added to agent '$agentId'"
                                }
                            } else {
                                HttpStatusCode.BadRequest to "Agent '$agentId' not found"
                            }
                        }
                        else -> HttpStatusCode.BadRequest to "Plugin $pluginId not found."
                    }
                    logger.debug { msg }
                    call.respond(status, msg)
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
                                                "/plugins/togglePlugin", pluginId
                                            )
                                )
                            )
                            HttpStatusCode.OK to "OK"
                        }
                    }
                    logger.debug { response }
                    call.respond(statusCode, response)
                }
            }

        }
    }
}
