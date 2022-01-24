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
package com.epam.drill.e2e

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.api.agent.*
import com.epam.drill.admin.api.group.*
import com.epam.drill.admin.api.plugin.*
import com.epam.drill.admin.api.routes.*
import com.epam.drill.admin.api.websocket.*
import com.epam.drill.admin.build.*
import com.epam.drill.admin.common.*
import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.notification.*
import com.epam.drill.admin.router.*
import com.epam.drill.api.*
import com.epam.drill.plugin.api.message.*
import com.epam.drill.plugin.api.processing.*
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.json.*
import kotlinx.serialization.protobuf.*
import org.apache.bcel.classfile.*
import java.io.*
import kotlin.reflect.full.*


abstract class PluginStreams {
    lateinit var app: Application
    lateinit var info: PluginTestContext
    abstract fun queued(incoming: ReceiveChannel<Frame>, out: SendChannel<Frame>, isDebugStream: Boolean = false)
    open suspend fun initSubscriptions(subscription: AgentSubscription) {}
    abstract suspend fun subscribe(subscription: AgentSubscription, destination: String)
}


class AdminUiChannels {
    val agentChannel = Channel<AgentInfoDto?>(Channel.UNLIMITED)
    val buildChannel = Channel<AgentBuildInfoDto?>(Channel.UNLIMITED)
    val buildSummaryChannel = Channel<List<BuildSummaryDto>?>(Channel.UNLIMITED)
    val agentsChannel = Channel<GroupedAgentsDto?>(Channel.UNLIMITED)
    val allPluginsChannel = Channel<Set<PluginDto>?>(Channel.UNLIMITED)
    val notificationsChannel = Channel<Set<Notification>?>(Channel.UNLIMITED)
    val agentPluginInfoChannel = Channel<Set<PluginDto>?>(Channel.UNLIMITED)

    suspend fun getAgent() = agentChannel.receive()
    suspend fun getBuild() = buildChannel.receive()
    suspend fun getBuildSummary() = buildSummaryChannel.receive()
    suspend fun getAllAgents() = agentsChannel.receive()
    suspend fun getAllPluginsInfo() = allPluginsChannel.receive()
    suspend fun getNotifications() = notificationsChannel.receive()
    suspend fun getAgentPluginInfo() = agentPluginInfoChannel.receive()

}

class UIEVENTLOOP(
    val cs: Map<String, AdminUiChannels>,
    val uiStreamDebug: Boolean,
    val glob: Channel<GroupedAgentsDto> = Channel(),
) {

    fun Application.queued(wsTopic: WsTopic, incoming: ReceiveChannel<Frame>) = launch {
        incoming.consumeEach { frame ->
            when (frame) {
                is Frame.Text -> {
                    val parsedJson = frame.readText().parseJson() as JsonObject
                    if (uiStreamDebug) {
                        println("UI: $parsedJson")
                    }
                    val messageType = WsMessageType.valueOf((parsedJson["type"] as JsonPrimitive).content)
                    val url = (parsedJson[WsSendMessage::destination.name] as JsonPrimitive).content
                    val content = parsedJson[WsSendMessage::message.name]
                    val (_, type) = wsTopic.getParams(url)
                    val response = content?.takeIf { it != JsonPrimitive("") }
                    when (messageType) {
                        WsMessageType.MESSAGE, WsMessageType.DELETE -> launch {
                            when (type) {
                                is WsRoutes.Agents -> glob.run {
                                    response?.let {
                                        send(GroupedAgentsDto.serializer() fromJson it)
                                    }
                                    println("Processed $type")
                                }
                                is WsRoot.Agent -> cs.getValue(type.agentId).agentChannel.run {
                                    send(response?.let { AgentInfoDto.serializer() fromJson it })
                                    println("Processed $type")
                                }
                                is WsRoot.AgentBuild -> cs.getValue(type.agentId).buildChannel.run {
                                    send(response?.let { AgentBuildInfoDto.serializer() fromJson it })
                                    println("Processed $type")
                                }
                                is WsRoutes.AgentBuildsSummary -> cs.getValue(type.agentId).buildSummaryChannel.run {
                                    send(response?.let { ListSerializer(BuildSummaryDto.serializer()) fromJson it })
                                    println("Processed $type")
                                }
                                is WsRoutes.AgentPlugins -> cs.getValue(type.agentId).agentPluginInfoChannel.run {
                                    send(response?.let { SetSerializer(PluginDto.serializer()) fromJson it })
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
    val agentStreamDebug: Boolean,
) {
    private val headers = Channel<Int>(Channel.UNLIMITED)
    private val `set-packages-prefixes` = Channel<String>(Channel.UNLIMITED)
    private val `load-classes-data` = Channel<String>(Channel.UNLIMITED)
    private val plugins = Channel<com.epam.drill.common.PluginBinary>(Channel.UNLIMITED)

    lateinit var plugin: AgentPart<*>

    suspend fun getHeaders() = headers.receive()

    suspend fun getLoadedPlugin(block: suspend (com.epam.drill.common.PluginMetadata, ByteArray) -> Unit) {
        val (meta, data) = plugins.receive()
        block(meta, data)
    }

    suspend fun loaded(pluginId: String) {
        sendDelivered("/agent/plugin/$pluginId/loaded")
    }

    suspend fun toggled(pluginId: String) {
        sendDelivered("/agent/plugin/${pluginId}/toggle")
    }

    suspend fun sendPluginData(data: MessageWrapper) {
        outgoing.send(
            agentMessage(
                MessageType.PLUGIN_DATA, "",
                (MessageWrapper.serializer() stringify data).encodeToByteArray()
            )
        )
    }

    suspend fun `get-set-packages-prefixes`(): String = `set-packages-prefixes`.receive()

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
        }.chunked(32768).forEach {
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
        }.chunked(32768).forEach {
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
        incoming.consumeEach { frame ->
            when (frame) {
                is Frame.Binary -> {
                    val load = ProtoBuf.load(Message.serializer(), frame.readBytes())
                    val url = load.destination
                    val content = load.data
                    if (agentStreamDebug) {
                        println("Agent $agentId <<< url=$url, size=${content.size}")
                    }
                    when (mapw[url]) {
                        is Communication.Agent.SetPackagePrefixesEvent -> {
                            `set-packages-prefixes`.send(content.decodeToString())
                            sendDelivered(url)
                        }
                        is Communication.Agent.LoadClassesDataEvent -> {
                            `load-classes-data`.send(content.decodeToString())
                        }
                        is Communication.Agent.PluginLoadEvent -> {
                            plugins.send(
                                ProtoBuf.load(
                                    com.epam.drill.common.PluginBinary.serializer(),
                                    content
                                )
                            )
                            sendDelivered(url)
                        }
                        is Communication.Agent.ChangeHeaderNameEvent -> {
                            headers.send(0)
                            sendDelivered(url)
                        }

                        is Communication.Plugin.DispatchEvent -> {
                            val message = ProtoBuf.load(
                                com.epam.drill.common.PluginAction.serializer(),
                                content
                            ).message
                            plugin.doRawAction(message)
                            //TODO EPMDJ-9807 Remove base64
                            sendDelivered("/plugin/action/${message.encodeBase64()}")
                            sendDelivered(url)
                        }
                        is Communication.Plugin.ToggleEvent -> {
                            val pluginId = ProtoBuf.load(com.epam.drill.common.TogglePayload.serializer(), content).pluginId
                            toggled(pluginId)
                            sendDelivered(url)
                        }
                        else -> sendDelivered(url)
                    }
                }
                else -> {
                    println("!!!$frame not handled!")
                }
            }
        }
    }

    private suspend fun sendDelivered(url: String) {
        outgoing.send(agentMessage(MessageType.MESSAGE_DELIVERED, url))
    }
}
