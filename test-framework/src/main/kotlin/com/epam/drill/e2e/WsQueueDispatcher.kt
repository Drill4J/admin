package com.epam.drill.e2e

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.api.agent.*
import com.epam.drill.admin.api.plugin.*
import com.epam.drill.admin.api.websocket.*
import com.epam.drill.admin.build.*
import com.epam.drill.admin.common.*
import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.notification.*
import com.epam.drill.admin.router.*
import com.epam.drill.admin.servicegroup.*
import com.epam.drill.api.*
import com.epam.drill.plugin.api.message.*
import com.epam.drill.plugin.api.processing.*
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.io.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.json.*
import kotlinx.serialization.protobuf.*
import org.apache.bcel.classfile.*
import kotlin.reflect.full.*


abstract class PluginStreams {
    lateinit var app: Application
    lateinit var info: PluginTestContext
    abstract fun queued(incoming: ReceiveChannel<Frame>, out: SendChannel<Frame>, isDebugStream: Boolean = false)
    abstract suspend fun subscribe(sinf: AgentSubscription, destination: String = "")
}


class AdminUiChannels {
    val agentChannel = Channel<AgentInfoDto?>(3)
    val buildsChannel = Channel<List<BuildSummaryDto>?>()
    val agentsChannel = Channel<GroupedAgentsDto?>()
    val allPluginsChannel = Channel<Set<PluginDto>?>()
    val notificationsChannel = Channel<Set<Notification>?>()
    val agentPluginInfoChannel = Channel<Set<PluginDto>?>()

    suspend fun getAgent() = agentChannel.receive()
    suspend fun getBuilds() = buildsChannel.receive()
    suspend fun getAllAgents() = agentsChannel.receive()
    suspend fun getAllPluginsInfo() = allPluginsChannel.receive()
    suspend fun getNotifications() = notificationsChannel.receive()
    suspend fun getAgentPluginInfo() = agentPluginInfoChannel.receive()

}

class UIEVENTLOOP(
    val cs: Map<String, AdminUiChannels>,
    val uiStreamDebug: Boolean,
    val glob: Channel<GroupedAgentsDto> = Channel()
) {

    fun Application.queued(wsTopic: WsTopic, incoming: ReceiveChannel<Frame>) = launch {
        incoming.consumeEach { frame ->
            when (frame) {
                is Frame.Text -> {
                    val parsedJson = frame.readText().parseJson() as JsonObject
                    if (uiStreamDebug) {
                        println("UI: $parsedJson")
                    }
                    val messageType = WsMessageType.valueOf(parsedJson["type"]!!.content)
                    val url = parsedJson[WsSendMessage::destination.name]!!.content
                    val content = parsedJson[WsSendMessage::message.name]!!.toString()
                    val (_, type) = wsTopic.getParams(url)
                    val response = content.takeIf { it != "\"\"" }
                    when (messageType) {
                        WsMessageType.MESSAGE, WsMessageType.DELETE -> launch {
                            when (type) {
                                is WsRoutes.Agents -> glob.run {
                                    send(GroupedAgentsDto.serializer() parse content)
                                    println("Processed $type")
                                }
                                is WsRoutes.Agent -> cs.getValue(type.agentId).agentChannel.run {
                                    send(response?.run { AgentInfoDto.serializer() parse content })
                                    println("Processed $type")
                                }
                                is WsRoutes.AgentBuilds -> cs.getValue(type.agentId).buildsChannel.run {
                                    send(response?.run { BuildSummaryDto.serializer().list parse content })
                                    println("Processed $type")
                                }
                                is WsRoutes.AgentPlugins -> cs.getValue(type.agentId).agentPluginInfoChannel.run {
                                    send(response?.run { PluginDto.serializer().set parse content })
                                    println("Processed $type")
                                }
                            }
                        }
                        else -> Unit
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
    private val headers = Channel<Int>()
    private val `set-packages-prefixes` = Channel<String>()
    private val `load-classes-data` = Channel<String>()
    private val plugins = Channel<com.epam.drill.common.PluginBinary>()
    private val pluginBinary = Channel<ByteArray>()
    lateinit var plugin: AgentPart<*, *>

    suspend fun getHeaders() = headers.receive()
    suspend fun getLoadedPlugin(block: suspend (com.epam.drill.common.PluginMetadata, ByteArray) -> Unit) {
        val (meta, data) = plugins.receive()
        block(meta, data)
    }

    suspend fun loaded(pluginId: String) {
        sendDelivered("/agent/load")
        sendDelivered("/agent/plugin/$pluginId/loaded")
    }

    suspend fun sendPluginData(data: MessageWrapper) {
        outgoing.send(
            agentMessage(
                MessageType.PLUGIN_DATA, "",
                (MessageWrapper.serializer() stringify data).encodeToByteArray()
            )
        )
    }

    suspend fun getPluginBinary() = pluginBinary.receive()
    suspend fun `get-set-packages-prefixes`(): String {
        val receive = `set-packages-prefixes`.receive()
        sendDelivered("/agent/set-packages-prefixes")
        return receive
    }

    suspend fun `get-load-classes-datas`(vararg classes: String = emptyArray()): String {
        val receive = `load-classes-data`.receive()

        outgoing.send(agentMessage(MessageType.START_CLASSES_TRANSFER, ""))


        classes.map {

            val readBytes = this::class.java.getResourceAsStream("/classes/$it").readBytes()
            val parse = ClassParser(ByteArrayInputStream(readBytes), "").parse()

            ProtoBuf.dump(
                com.epam.drill.common.ByteClass.serializer(), com.epam.drill.common.ByteClass(
                    parse.className.replace(".", "/"),
                    readBytes
                )
            )
        }.chunked(10).forEach {
            outgoing.send(
                agentMessage(
                    MessageType.CLASSES_DATA, "", ProtoBuf.dump(
                        ByteArrayListWrapper.serializer(), ByteArrayListWrapper(it)
                    )
                )
            )
        }


        outgoing.send(agentMessage(MessageType.FINISH_CLASSES_TRANSFER, ""))
        sendDelivered("/agent/load-classes-data")
        return receive
    }

    suspend fun `get-load-classes-data`(vararg classes: com.epam.drill.common.ByteClass = emptyArray()): String {
        val receive = `load-classes-data`.receive()
        outgoing.send(agentMessage(MessageType.START_CLASSES_TRANSFER, ""))
        classes.map { byteClass ->
            ProtoBuf.dump(com.epam.drill.common.ByteClass.serializer(), byteClass)
        }.chunked(10).forEach {
            outgoing.send(
                agentMessage(
                    MessageType.CLASSES_DATA, "", ProtoBuf.dump(
                        ByteArrayListWrapper.serializer(), ByteArrayListWrapper(it)
                    )
                )
            )
        }


        outgoing.send(agentMessage(MessageType.FINISH_CLASSES_TRANSFER, ""))
        sendDelivered("/agent/load-classes-data")
        return receive
    }

    fun queued() = app.launch {
        if (agentStreamDebug) {
            println()
            println("______________________________________________________________")
        }
        val mapw = Communication::class.nestedClasses.map { it.nestedClasses }.flatten().associate { cls ->
            val newInstance = cls.createInstance()
            (cls.annotations[1] as Topic).url to newInstance
        }
        incoming.consumeEach {
            when (it) {
                is Frame.Binary -> {


                    val load = ProtoBuf.load(Message.serializer(), it.readBytes())
                    val url = load.destination
                    val content = load.data
                    if (agentStreamDebug)
                        println("AGENT $agentId IN: $load")

                    app.launch {
                        when (mapw[url]) {
                            is Communication.Agent.SetPackagePrefixesEvent -> `set-packages-prefixes`.send(content.decodeToString())
                            is Communication.Agent.LoadClassesDataEvent -> `load-classes-data`.send(content.decodeToString())
                            is Communication.Agent.PluginLoadEvent -> plugins.send(
                                ProtoBuf.load(
                                    com.epam.drill.common.PluginBinary.serializer(),
                                    content
                                )
                            )
                            is Communication.Agent.ChangeHeaderNameEvent -> {
                                headers.send(0)
                            }

                            is Communication.Plugin.DispatchEvent -> {
                                plugin.doRawAction(
                                    (ProtoBuf.load(
                                        com.epam.drill.common.PluginAction.serializer(),
                                        content
                                    )).message
                                )
                                sendDelivered(url)
                            }
                            is Communication.Plugin.ToggleEvent -> sendDelivered(url)
                            is Communication.Plugin.UnloadEvent -> {
                            }
                            is Communication.Plugin.ResetEvent -> {
                            }
                            else -> {
                                sendDelivered(url)
                                TODO("$url is not implemented yet")
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun sendDelivered(url: String) {
        outgoing.send(agentMessage(MessageType.MESSAGE_DELIVERED, url))
    }
}

