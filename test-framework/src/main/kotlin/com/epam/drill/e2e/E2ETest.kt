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

import com.epam.drill.admin.api.routes.*
import com.epam.drill.admin.api.agent.*
import com.epam.drill.admin.api.group.*
import com.epam.drill.admin.api.plugin.*
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


abstract class E2ETest : AdminTest() {
    private val agents = ConcurrentHashMap<AgentKey, AgentStruct>()

    fun createSimpleAppWithUIConnection(
        uiStreamDebug: Boolean = false,
        agentStreamDebug: Boolean = false,
        timeout: Duration = 7.seconds,
        block: suspend () -> Unit
    ) {
        var coroutineException: Throwable? = null
        val context = SupervisorJob()
        val handler = CoroutineExceptionHandler { _, exception ->
            coroutineException = exception
        } + context
        runBlocking(handler) {
            val timeoutJob = createTimeoutJob(timeout, context)
            val appConfig = AppConfig(projectDir)
            val testApp = appConfig.testApp
            withApplication(
                environment = createTestEnvironment { parentCoroutineContext = context },
                configure = { dispatcher = Dispatchers.IO + context }
            ) {
                testApp(application, sslPort)
                asyncEngine = AsyncTestAppEngine(handler, this)
                storeManager = appConfig.storeManager
                commonStore = appConfig.commonStore
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
                    val cs = mutableMapOf<String, AdminUiChannels>()
                    coroutineScope {
                        agents.map { (_, xx) ->
                            val (ag, startClb, connect, thens) = xx
                            val ui = AdminUiChannels()
                            cs[ag.id] = ui
                            launch(handler) {
                                startClb()
                                val uiE = UIEVENTLOOP(cs, uiStreamDebug, glob)
                                with(uiE) { application.queued(appConfig.wsTopic, uiIncoming) }

                                //create the '/agent/attach' websocket connection
                                ut.send(uiMessage(Subscribe("/agents/${ag.id}")))
                                ut.send(uiMessage(Subscribe("/agents/${ag.id}/builds")))

                                ui.getAgent()
                                ui.getBuilds()
                                delay(50)

                                handleWebSocketConversation(
                                    "/agent/attach",
                                    wsRequestRequiredParams(ag)
                                ) { inp, out ->
                                    glob.receive()
                                    val apply = Agent(application, ag.id, inp, out, agentStreamDebug).apply { queued() }
                                    connect(
                                        asyncEngine,
                                        ui,
                                        apply
                                    )
                                    while (globLaunch.isActive)
                                        delay(100)

                                }
                                thens.forEach { (ain, it) ->
                                    glob.receive()
                                    ui.getAgent()
                                    ui.getBuilds()
                                    delay(50)

                                    handleWebSocketConversation(
                                        "/agent/attach",
                                        wsRequestRequiredParams(ain)
                                    ) { inp, out ->
                                        val apply =
                                            Agent(application, ain.id, inp, out, agentStreamDebug).apply { queued() }
                                        it(
                                            asyncEngine,
                                            ui,
                                            apply
                                        )
                                        while (globLaunch.isActive)
                                            delay(100)
                                    }
                                }
                            }
                        }.forEach { it.join() }
                        globLaunch.join()
                    }

                }
            }
            timeoutJob.cancel()
        }
        if (coroutineException != null) {
            throw coroutineException!!
        }
    }

    fun connectAgent(
        ags: AgentWrap,
        startClb: suspend () -> Unit = {},
        bl: suspend AsyncTestAppEngine.(AdminUiChannels, Agent) -> Unit
    ): E2ETest {
        agents[AgentKey(ags.id, ags.instanceId)] = AgentStruct(ags, startClb, bl, mutableListOf())
        return this
    }


    fun reconnect(
        ags: AgentWrap,
        bl: suspend AsyncTestAppEngine.(AdminUiChannels, Agent) -> Unit
    ): E2ETest {
        agents[AgentKey(ags.id, ags.instanceId)]?.reconnects?.add(ags to bl)
        return this
    }

    fun AsyncTestAppEngine.addPlugin(
        agentId: String,
        payload: PluginId,
        token: String = globToken,
        resultBlock: suspend (HttpStatusCode?, String?) -> Unit = { _, _ -> }
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
        resultBlock: suspend (HttpStatusCode?, String?) -> Unit = { _, _ -> }
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
        resultBlock: suspend (HttpStatusCode?, String?) -> Unit = { _, _ -> }
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
        resultBlock: suspend (HttpStatusCode?, String?) -> Unit = { _, _ -> }
    ) {
        callAsync(context) {
            with(engine) {
                handleRequest(
                    HttpMethod.Post,
                    toApiUri(
                        ApiRoot().let(ApiRoot::Agents).let { ApiRoot.Agents.TogglePlugin(it, agentId, pluginId.pluginId) }
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
        resultBlock: suspend (HttpStatusCode?, String?) -> Unit = { _, _ -> }
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
        resultBlock: suspend (HttpStatusCode?, String?) -> Unit = { _, _ -> }
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
    val startClb: suspend () -> Unit = {},
    val clbs: suspend AsyncTestAppEngine.(AdminUiChannels, Agent) -> Unit,
    val reconnects: MutableList<Pair<AgentWrap, suspend AsyncTestAppEngine.(AdminUiChannels, Agent) -> Unit>>
)

data class AgentWrap(
    val id: String,
    val instanceId: String = id + "1",
    val buildVersion: String = "0.1.0",
    val groupId: String = "",
    val needSync: Boolean = true,
    val agentType: AgentType = AgentType.JAVA
)
