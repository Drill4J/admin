package com.epam.drill.e2e

import com.epam.drill.agentmanager.*
import com.epam.drill.common.*
import com.epam.drill.common.ws.*
import com.epam.drill.dataclasses.*
import com.epam.drill.endpoints.*
import com.epam.drill.endpoints.plugin.*
import com.epam.drill.plugin.api.message.*
import com.epam.drill.plugin.api.processing.*
import com.epam.drill.plugins.*
import com.epam.drill.router.*
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.io.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import org.apache.bcel.classfile.*

abstract class PluginStreams {
    lateinit var app: Application
    lateinit var info: PluginTestContext
    abstract fun queued(incoming: ReceiveChannel<Frame>, out: SendChannel<Frame>, isDebugStream: Boolean = false)
    abstract suspend fun subscribe(sinf: SubscribeInfo, destination: String = "")
}


class AdminUiChannels {
    val agentChannel = Channel<AgentInfoWebSocket?>()
    val agentBuildsChannel = Channel<Set<AgentBuildVersionJson>?>()
    val buildsChannel = Channel<List<BuildSummaryWebSocket>?>()
    val agentsChannel = Channel<Set<AgentInfoWebSocket>?>()
    val allPluginsChannel = Channel<Set<PluginWebSocket>?>()
    val notificationsChannel = Channel<Set<Notification>?>()
    val agentPluginInfoChannel = Channel<Set<PluginWebSocket>?>()

    suspend fun getAgent() = agentChannel.receive()
    suspend fun getAgentBuilds() = agentBuildsChannel.receive()
    suspend fun getBuilds() = buildsChannel.receive()
    suspend fun getAllAgents() = agentsChannel.receive()
    suspend fun getAllPluginsInfo() = allPluginsChannel.receive()
    suspend fun getNotifications() = notificationsChannel.receive()
    suspend fun getAgentPluginInfo() = agentPluginInfoChannel.receive()

}

class UIEVENTLOOP(
    val cs: Map<String, AdminUiChannels>,
    val uiStreamDebug: Boolean,
    val glob: Channel<Set<AgentInfoWebSocket>> = Channel()
) {

    fun Application.queued(wsTopic: WsTopic, incoming: ReceiveChannel<Frame>) = this.launch {
        incoming.consumeEach {
            when (it) {
                is Frame.Text -> {
                    val parseJson = json.parseJson(it.readText()) as JsonObject
                    if (uiStreamDebug)
                        println("UI: $parseJson")
                    val messageType = WsMessageType.valueOf(parseJson[WsReceiveMessage::type.name]!!.content)
                    val url = parseJson[WsReceiveMessage::destination.name]!!.content
                    val content = parseJson[WsReceiveMessage::message.name]!!.toString()
                    val (_, type) = wsTopic.getParams(url)
                    val notEmptyResponse = content != "\"\""
                    when (messageType) {
                        WsMessageType.MESSAGE,WsMessageType.DELETE ->
                            this@queued.launch {
                                when (type) {
                                    is WsRoutes.GetAllAgents -> {
                                        glob.send(AgentInfoWebSocket.serializer().set parse content)
                                    }
                                    is WsRoutes.GetAgent -> {

                                        if (notEmptyResponse) {
                                            cs[type.agentId]!!.agentChannel.send(AgentInfoWebSocket.serializer() parse content)
                                        } else {
                                            cs[type.agentId]!!.agentChannel.send(null)
                                        }

                                    }
                                    is WsRoutes.GetAgentBuilds -> {
                                        if (notEmptyResponse) {
                                            cs[type.agentId]!!.agentBuildsChannel.send((AgentBuildVersionJson.serializer().set parse content))
                                        } else {
                                            cs[type.agentId]!!.agentBuildsChannel.send(null)
                                        }
                                    }
                                    is WsRoutes.GetAllPlugins -> {
                                        //                                if (notEmptyResponse) {
//                                    allPluginsChannel.send((PluginWebSocket.serializer().set parse content))
//                                } else {
//                                    allPluginsChannel.send(null)
//                                }
                                    }

                                    is WsRoutes.GetBuilds -> {
                                        if (notEmptyResponse) {
                                            cs.getValue(type.agentId)
                                                .buildsChannel.send((BuildSummaryWebSocket.serializer().list parse content))
                                        } else {
                                            cs.getValue(type.agentId).buildsChannel.send(null)
                                        }
                                    }

                                    is WsRoutes.GetNotifications -> {
                                        //                                if (notEmptyResponse) {
//                                    notificationsChannel.send(Notification.serializer().set parse content)
//                                } else {
//                                    notificationsChannel.send(null)
//                                }
                                    }

                                    is WsRoutes.GetPluginConfig -> {
                                    }
                                    is WsRoutes.GetPluginInfo -> {
                                        if (notEmptyResponse) {
                                            cs[type.agentId]!!.agentPluginInfoChannel.send(PluginWebSocket.serializer().set parse content)
                                        } else {
                                            cs[type.agentId]!!.agentPluginInfoChannel.send(null)
                                        }
                                    }
                                }
                            }
                        else -> {
                        }
                    }
                }
                is Frame.Close -> {
                }
                else -> throw RuntimeException(" read not FRAME.TEXT frame.")
            }
        }

    }
}


class Agent(
    val app: Application,
    val agentId: String,
    val incoming: ReceiveChannel<Frame>,
    val outgoing: SendChannel<Frame>,
    val agentStreamDebug: Boolean
) {
    private val serviceConfig = Channel<ServiceConfig?>()
    private val `set-packages-prefixes` = Channel<String>()
    private val `load-classes-data` = Channel<String>()
    private val plugins = Channel<PluginMetadata>()
    private val pluginBinary = Channel<ByteArray>()
    lateinit var plugin: AgentPart<*, *>

    suspend fun getServiceConfig() = serviceConfig.receive()
    suspend fun getLoadedPlugin(block: suspend (PluginMetadata, ByteArray) -> Unit) {
        val receive = plugins.receive()
        val pluginBinarys = getPluginBinary()
        block(receive, pluginBinarys)
        outgoing.send(AgentMessage(MessageType.MESSAGE_DELIVERED, "/plugins/load", ""))

    }

    suspend fun sendPluginData(data: MessageWrapper) {
        outgoing.send(
            AgentMessage(
                MessageType.PLUGIN_DATA, "",
                MessageWrapper.serializer() stringify data
            )
        )
    }

    suspend fun getPluginBinary() = pluginBinary.receive()
    suspend fun `get-set-packages-prefixes`(): String {
        val receive = `set-packages-prefixes`.receive()
        outgoing.send(
            AgentMessage(
                MessageType.MESSAGE_DELIVERED,
                "/agent/set-packages-prefixes",
                ""
            )
        )
        return receive
    }

    suspend fun `get-load-classes-datas`(vararg classes: String = emptyArray()): String {
        val receive = `load-classes-data`.receive()

        outgoing.send(AgentMessage(MessageType.START_CLASSES_TRANSFER, "", ""))


        classes.forEach {

            val readBytes = this::class.java.getResourceAsStream("/classes/$it").readBytes()
            val parse = ClassParser(ByteArrayInputStream(readBytes), "").parse()

            outgoing.send(
                AgentMessage(
                    MessageType.CLASSES_DATA, "", Base64Class.serializer() stringify Base64Class(
                        parse.className.replace(".", "/"),
                        readBytes.encodeBase64()
                    )
                )
            )
        }


        outgoing.send(AgentMessage(MessageType.FINISH_CLASSES_TRANSFER, "", ""))
        outgoing.send(
            AgentMessage(
                MessageType.MESSAGE_DELIVERED,
                "/agent/load-classes-data",
                ""
            )
        )
        return receive
    }

    suspend fun `get-load-classes-data`(vararg classes: JavaClass = emptyArray()): String {
        val receive = `load-classes-data`.receive()
        outgoing.send(AgentMessage(MessageType.START_CLASSES_TRANSFER, "", ""))
        classes.forEach { bclass ->
            outgoing.send(
                AgentMessage(
                    MessageType.CLASSES_DATA, "", Base64Class.serializer() stringify Base64Class(
                        bclass.className.replace(".", "/"),
                        bclass.bytes.encodeBase64()
                    )
                )
            )
        }


        outgoing.send(AgentMessage(MessageType.FINISH_CLASSES_TRANSFER, "", ""))
        outgoing.send(
            AgentMessage(
                MessageType.MESSAGE_DELIVERED,
                "/agent/load-classes-data",
                ""
            )
        )
        return receive
    }

    fun queued() = app.launch {
        if (agentStreamDebug) {
            println()
            println("______________________________________________________________")
        }
        incoming.consumeEach {
            when (it) {
                is Frame.Text -> {
                    val parseJson = json.parseJson(it.readText()) as JsonObject
                    val url = parseJson[Message::destination.name]!!.content
                    val content = parseJson[Message::data.name]!!.content
                    if (agentStreamDebug)
                        println("AGENT $agentId IN: $parseJson")
                    app.launch {
                        when (url) {
                            "/agent/config" -> serviceConfig.send(ServiceConfig.serializer() parse content)
                            "/agent/set-packages-prefixes" -> `set-packages-prefixes`.send(content)
                            "/agent/load-classes-data" -> `load-classes-data`.send(content)
                            "/plugins/load" -> plugins.send(PluginMetadata.serializer() parse content)
                            "/plugins/resetPlugin" -> {
                            }
                            "/plugins/action" -> {
                                plugin.doRawAction((PluginAction.serializer() parse content).message)
                            }
                            "/plugins/unload" -> {
                            }
                            "/ping" -> {
                                outgoing.send(
                                    AgentMessage(
                                        MessageType.MESSAGE_DELIVERED,
                                        "/ping",
                                        ""
                                    )
                                )
                            }
                            "/plugins/togglePlugin" -> {
                                outgoing.send(
                                    AgentMessage(
                                        MessageType.MESSAGE_DELIVERED,
                                        "/ping",
                                        ""
                                    )
                                )
                            }
                            else -> TODO("$url is not implemented yet")
                        }
                    }
                }
                is Frame.Binary -> {
                    pluginBinary.send(it.readBytes())
                }
            }
        }
    }

}

