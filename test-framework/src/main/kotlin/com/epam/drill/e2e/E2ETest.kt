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

import com.epam.drill.admin.api.agent.*
import com.epam.drill.admin.api.group.*
import com.epam.drill.admin.api.plugin.*
import com.epam.drill.admin.api.routes.*
import com.epam.drill.admin.common.*
import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.testdata.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.util.concurrent.*
import kotlin.coroutines.*
import kotlin.time.*

typealias Instance = Pair<AgentKey, AgentStruct>

typealias AgentId = String

abstract class E2ETest : AdminTest() {

    private val agents = ConcurrentHashMap<AgentId, ConcurrentLinkedQueue<Instance>>()

    fun connectAgent(
        agent: AgentWrap,
        initBlock: suspend () -> Unit = {},
        connection: suspend AsyncTestAppEngine.(AdminUiChannels, Agent) -> Unit,
    ): E2ETest {
        if (agents[agent.id].isNullOrEmpty()) {
            agents[agent.id] = ConcurrentLinkedQueue(
                listOf(
                    AgentKey(agent.id, agent.instanceId) to AgentStruct(
                        agent,
                        initBlock,
                        connection,
                        mutableListOf()
                    )
                )
            )
        } else {
            agents[agent.id]?.add(
                AgentKey(agent.id, agent.instanceId) to AgentStruct(
                    agent,
                    initBlock,
                    connection,
                    mutableListOf()
                )
            )
        }
        return this
    }


    fun createSimpleAppWithUIConnection(
        uiStreamDebug: Boolean = false,
        agentStreamDebug: Boolean = false,
        timeout: Duration = 7.seconds,
        delayBeforeClearData: Long = 0,
        block: suspend () -> Unit,
    ) {
        var coroutineException: Throwable? = null
        val context = SupervisorJob()
        val handler = CoroutineExceptionHandler { _, exception ->
            coroutineException = exception
        } + context
        runBlocking(handler) {
            val timeoutJob = createTimeoutJob(timeout, context)
            val appConfig = AppConfig(projectDir, delayBeforeClearData)
            val testApp = appConfig.testApp
            withApplication(
                environment = createTestEnvironment { parentCoroutineContext = context },
                configure = { dispatcher = Dispatchers.IO + context }
            ) {
                testApp(application, sslPort)
                asyncEngine = AsyncTestAppEngine(handler, this)
                commonStore = appConfig.commonStore
                storeManager = appConfig.storeManager
                globToken = requestToken()
                //create the 'drill-admin-socket' websocket connection
                handleWebSocketConversation("/ws/drill-admin-socket?token=${globToken}") { uiIncoming, ut ->
                    block()
                    ut.send(uiMessage(Subscribe("/agents")))
                    uiIncoming.receive()
                    val glob = Channel<GroupedAgentsDto>()
                    val globLaunch = application.launch(handler) {
                        watcher?.invoke(asyncEngine, glob)
                    }

                    val agentUiChannel = ConcurrentHashMap<AgentId, AdminUiChannels>()
                    coroutineScope {
                        agents.map { (_, queue) ->
                            launch(handler) {
                                val currentInstance = queue.poll()
                                val agentId = currentInstance.first.id
                                if (agentUiChannel[agentId] == null) {
                                    val ui = AdminUiChannels()
                                    agentUiChannel[agentId] = ui
                                    val uiE = UIEVENTLOOP(agentUiChannel, uiStreamDebug, glob)
                                    with(uiE) { application.queued(appConfig.wsTopic, uiIncoming) }
                                    ut.send(uiMessage(Subscribe("/agents/$agentId")))
                                    ut.send(uiMessage(Subscribe("/agents/$agentId/builds")))
                                    ui.getAgent()
                                    ui.getBuilds()
                                    delay(50)
                                }
                                createWsConversation(
                                    currentInstance,
                                    glob,
                                    agentUiChannel[agentId]!!,
                                    globLaunch,
                                    agentStreamDebug,
                                    queue
                                )
                            }
                        }
                    }.forEach { it.join() }
                    globLaunch.join()
                }
            }
            timeoutJob.cancel()
        }
        if (coroutineException != null) {
            throw coroutineException!!
        }
    }

    private suspend fun TestApplicationEngine.createWsConversation(
        currentInstance: Instance,
        glob: Channel<GroupedAgentsDto>,
        ui: AdminUiChannels,
        globLaunch: Job,
        agentStreamDebug: Boolean,
        instances: ConcurrentLinkedQueue<Instance>,
    ): Unit = run {
        val (agentKey, agentStruct) = currentInstance
        agentStruct.intiBlock()

        handleWebSocketConversation(
            "/agent/attach",
            wsRequestRequiredParams(agentStruct.agWrap)
        ) { inp, out ->
            glob.receive()
            val agent = Agent(
                application,
                agentStruct.agWrap.id,
                inp,
                out,
                agentStreamDebug
            ).apply {
                delay(40)
                queued()
            }
            agentStruct.connection(
                asyncEngine,
                ui,
                agent
            )
            val nextInstance = instances.poll()
            if (nextInstance != null) {
                createWsConversation(nextInstance, glob, ui, globLaunch, agentStreamDebug, instances)
            }
            while (globLaunch.isActive)
                delay(100)
        }

        if (agentStreamDebug) println("Agent $agentKey disconnected")
        agentStruct.reconnects.forEach { (agentWrap, reconnect) ->
            if (agentStreamDebug) println("Agent $agentKey reconnected")
            glob.receive()
            ui.getAgent()
            ui.getBuilds()
            delay(50)

            handleWebSocketConversation(
                "/agent/attach",
                wsRequestRequiredParams(agentWrap)
            ) { inp, out ->
                val apply = Agent(application, agentWrap.id, inp, out, agentStreamDebug).apply { queued() }
                reconnect(
                    asyncEngine,
                    ui,
                    apply
                )
                while (globLaunch.isActive)
                    delay(100)
            }
        }
    }

    fun reconnect(
        ags: AgentWrap,
        bl: suspend AsyncTestAppEngine.(AdminUiChannels, Agent) -> Unit,
    ): E2ETest {
        agents[ags.id]?.filter {
            it.first == AgentKey(ags.id, ags.instanceId)
        }?.forEach { it.second.reconnects.add(ags to bl) }
        return this
    }

    fun AsyncTestAppEngine.addPlugin(
        agentId: String,
        payload: PluginId,
        token: String = globToken,
        resultBlock: suspend (HttpStatusCode?, String?) -> Unit = { _, _ -> },
    ) =
        callAsync(context) {
            with(engine) {
                handleRequest(
                    HttpMethod.Post,
                    toApiUri(agentApi { ApiRoot.Agents.Plugins(it, agentId) })
                ) {
                    addHeader(HttpHeaders.Authorization, "Bearer $token")
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(PluginId.serializer() stringify payload)
                }.apply { resultBlock(response.status(), response.content) }
            }
        }

    fun AsyncTestAppEngine.unregister(
        agentId: String,
        token: String = globToken,
        resultBlock: suspend (HttpStatusCode?, String?) -> Unit = { _, _ -> },
    ) =
        callAsync(context) {
            with(engine) {
                handleRequest(
                    HttpMethod.Delete,
                    toApiUri(agentApi { ApiRoot.Agents.Agent(it, agentId) })
                ) {
                    addHeader(HttpHeaders.Authorization, "Bearer $token")
                }.apply { resultBlock(response.status(), response.content) }
            }
        }

    fun AsyncTestAppEngine.unLoadPlugin(
        agentId: String,
        payload: PluginId,
        token: String = globToken,
        resultBlock: suspend (HttpStatusCode?, String?) -> Unit = { _, _ -> },
    ) {
        callAsync(context) {
            with(engine) {
                handleRequest(
                    HttpMethod.Delete,
                    toApiUri(agentApi { ApiRoot.Agents.Plugin(it, agentId, payload.pluginId) })
                ) {
                    addHeader(HttpHeaders.Authorization, "Bearer $token")
                }.apply { resultBlock(response.status(), response.content) }
            }
        }
    }

    fun AsyncTestAppEngine.togglePlugin(
        agentId: String,
        pluginId: PluginId,
        token: String = globToken,
        resultBlock: suspend (HttpStatusCode?, String?) -> Unit = { _, _ -> },
    ) {
        callAsync(context) {
            with(engine) {
                handleRequest(
                    HttpMethod.Post,
                    toApiUri(
                        ApiRoot().let(ApiRoot::Agents).let {
                            ApiRoot.Agents.TogglePlugin(it, agentId, pluginId.pluginId)
                        }
                    )
                ) {
                    addHeader(HttpHeaders.Authorization, "Bearer $token")
                }.apply { resultBlock(response.status(), response.content) }
            }
        }
    }

    fun AsyncTestAppEngine.toggleAgent(
        agentId: String,
        token: String = globToken,
        resultBlock: suspend (HttpStatusCode?, String?) -> Unit = { _, _ -> },
    ) {
        callAsync(context) {
            with(engine) {
                handleRequest(
                    HttpMethod.Post,
                    toApiUri(
                        ApiRoot().let(ApiRoot::Agents).let { ApiRoot.Agents.ToggleAgent(it, agentId) }
                    )
                ) {
                    addHeader(HttpHeaders.Authorization, "Bearer $token")
                }.apply { resultBlock(response.status(), response.content) }
            }
        }
    }

    fun AsyncTestAppEngine.changePackages(
        agentId: String,
        token: String = globToken,
        payload: SystemSettingsDto,
        resultBlock: suspend (HttpStatusCode?, String?) -> Unit = { _, _ -> },
    ) = callAsync(context) {
        with(engine) {
            handleRequest(
                HttpMethod.Put,
                toApiUri(agentApi { ApiRoot.Agents.SystemSettings(it, agentId) })
            ) {
                addHeader(HttpHeaders.Authorization, "Bearer $token")
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(SystemSettingsDto.serializer() stringify payload)
            }.apply { resultBlock(response.status(), response.content) }
        }
    }
}

data class AsyncTestAppEngine(val context: CoroutineContext, val engine: TestApplicationEngine)

data class AgentKey(val id: String, val instanceId: String)
data class AgentStruct(
    val agWrap: AgentWrap,
    val intiBlock: suspend () -> Unit = {},
    val connection: suspend AsyncTestAppEngine.(AdminUiChannels, Agent) -> Unit,
    val reconnects: MutableList<Pair<AgentWrap, suspend AsyncTestAppEngine.(AdminUiChannels, Agent) -> Unit>>,
)

data class AgentWrap(
    val id: String,
    val instanceId: String = id + "1",
    val buildVersion: String = "0.1.0",
    val groupId: String = "",
    val needSync: Boolean = true,
    val agentType: AgentType = AgentType.JAVA,
)
