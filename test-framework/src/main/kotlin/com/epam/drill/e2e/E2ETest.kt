package com.epam.drill.e2e

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.servicegroup.*
import com.epam.drill.common.*
import com.epam.drill.endpoints.*
import com.epam.drill.endpoints.agent.*
import com.epam.drill.router.*
import com.epam.drill.testdata.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.util.concurrent.*
import kotlin.time.*


abstract class E2ETest : AdminTest() {
    private val agents = ConcurrentHashMap<AgentKey, AgentStruct>()

    fun createSimpleAppWithUIConnection(
        uiStreamDebug: Boolean = false,
        agentStreamDebug: Boolean = false,
        block: suspend () -> Unit
    ) {
        runBlocking {
            val context = SupervisorJob()
            val timeoutJob = createTimeoutJob(5.seconds, context)
            val appConfig = AppConfig(projectDir)
            val testApp = appConfig.testApp
            var coroutineException: Throwable? = null
            val handler = CoroutineExceptionHandler { _, exception -> coroutineException = exception } + context
            withApplication(
                environment = createTestEnvironment { parentCoroutineContext = context },
                configure = { dispatcher = Dispatchers.IO + context })
            {
                testApp(application, sslPort, false)
                storeManager = appConfig.storeManager
                commonStore = appConfig.commonStore
                globToken = requestToken()
                //create the 'drill-admin-socket' websocket connection
                handleWebSocketConversation("/ws/drill-admin-socket?token=${globToken}") { uiIncoming, ut ->
                    block()
                    ut.send(UiMessage(WsMessageType.SUBSCRIBE, "/get-all-agents"))
                    uiIncoming.receive()
                    val glob = Channel<GroupedAgentsDto>()
                    val globLaunch = application.launch(handler) {
                        watcher?.invoke(this@withApplication, glob)
                    }
                    val cs = mutableMapOf<String, AdminUiChannels>()
                    runBlocking(handler) {
                        agents.map { (_, xx) ->
                            val (ag, startClb, connect, thens) = xx
                            launch(handler) {
                                startClb()
                                val ui = AdminUiChannels()
                                cs[ag.id] = ui
                                val uiE = UIEVENTLOOP(cs, uiStreamDebug, glob)
                                with(uiE) { application.queued(appConfig.wsTopic, uiIncoming) }

                                //create the '/agent/attach' websocket connection
                                ut.send(UiMessage(WsMessageType.SUBSCRIBE, "/get-agent/${ag.id}"))
                                ut.send(UiMessage(WsMessageType.SUBSCRIBE, "/${ag.id}/builds"))

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
                                        this@withApplication,
                                        ui,
                                        apply
                                    )
                                    while (globLaunch.isActive)
                                        delay(100)

                                }
                                thens.forEach { (ain, it) ->

                                    handleWebSocketConversation(
                                        "/agent/attach",
                                        wsRequestRequiredParams(ain)
                                    ) { inp, out ->
                                        glob.receive()
                                        ui.getAgent()
                                        ui.getBuilds()
                                        val apply =
                                            Agent(application, ain.id, inp, out, agentStreamDebug).apply { queued() }
                                        it(
                                            this@withApplication,
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
                if (coroutineException != null) {
                    throw coroutineException as Throwable
                }
            }
            timeoutJob.cancel()
        }
    }

    fun connectAgent(
        ags: AgentWrap,
        startClb: suspend () -> Unit = {},
        bl: suspend TestApplicationEngine.(AdminUiChannels, Agent) -> Unit
    ): E2ETest {
        agents[AgentKey(ags.id, ags.instanceId)] = AgentStruct(ags, startClb, bl, mutableListOf())
        return this
    }


    fun reconnect(
        ags: AgentWrap,
        bl: suspend TestApplicationEngine.(AdminUiChannels, Agent) -> Unit
    ): E2ETest {
        agents[AgentKey(ags.id, ags.instanceId)]?.reconnects?.add(ags to bl)
        return this
    }

    fun TestApplicationEngine.register(
        agentId: String,
        token: String = globToken,
        payload: AgentRegistrationInfo = AgentRegistrationInfo(
            name = "xz",
            description = "ad",
            packagesPrefixes = listOf("testPrefix"),
            plugins = emptyList()
        ),
        resultBlock: suspend (HttpStatusCode?, String?) -> Unit = { _, _ -> }
    ) = callAsync {
        handleRequest(HttpMethod.Post, "/api" + application.locations.href(Routes.Api.Agent.RegisterAgent(agentId))) {
            addHeader(HttpHeaders.Authorization, "Bearer $token")
            setBody(AgentRegistrationInfo.serializer() stringify payload)
        }.apply { resultBlock(response.status(), response.content) }
    }

    fun TestApplicationEngine.addPlugin(
        agentId: String,
        payload: PluginId,
        token: String = globToken,
        resultBlock: suspend (HttpStatusCode?, String?) -> Unit = { _, _ -> }
    ) =
        callAsync {
            handleRequest(
                HttpMethod.Post,
                "/api" + application.locations.href(Routes.Api.Agent.AddNewPlugin(agentId))
            ) {
                addHeader(HttpHeaders.Authorization, "Bearer $token")
                setBody(PluginId.serializer() stringify payload)
            }.apply { resultBlock(response.status(), response.content) }
        }

    fun TestApplicationEngine.unRegister(
        agentId: String,
        token: String = globToken,
        resultBlock: suspend (HttpStatusCode?, String?) -> Unit = { _, _ -> }
    ) =
        callAsync {
            handleRequest(
                HttpMethod.Post,
                "/api" + application.locations.href(Routes.Api.Agent.UnregisterAgent(agentId))
            ) {
                addHeader(HttpHeaders.Authorization, "Bearer $token")
            }.apply { resultBlock(response.status(), response.content) }
        }

    fun TestApplicationEngine.unLoadPlugin(
        agentId: String,
        payload: PluginId,
        token: String = globToken,
        resultBlock: suspend (HttpStatusCode?, String?) -> Unit = { _, _ -> }
    ) {
        callAsync {
            handleRequest(
                HttpMethod.Post,
                "/api" + application.locations.href(Routes.Api.Agent.UnloadPlugin(agentId, payload.pluginId))
            ) {
                addHeader(HttpHeaders.Authorization, "Bearer $token")
            }.apply { resultBlock(response.status(), response.content) }
        }
    }

    fun TestApplicationEngine.togglePlugin(
        agentId: String,
        pluginId: PluginId,
        token: String = globToken,
        resultBlock: suspend (HttpStatusCode?, String?) -> Unit = { _, _ -> }
    ) {
        callAsync {
            handleRequest(
                HttpMethod.Post,
                "/api" + application.locations.href(Routes.Api.Agent.TogglePlugin(agentId, pluginId.pluginId))
            ) {
                addHeader(HttpHeaders.Authorization, "Bearer $token")
            }.apply { resultBlock(response.status(), response.content) }
        }
    }

    fun TestApplicationEngine.toggleAgent(
        agentId: String,
        token: String = globToken,
        resultBlock: suspend (HttpStatusCode?, String?) -> Unit = { _, _ -> }
    ) {
        callAsync {
            handleRequest(
                HttpMethod.Post,
                "/api" + application.locations.href(Routes.Api.Agent.AgentToggleStandby(agentId))
            ) {
                addHeader(HttpHeaders.Authorization, "Bearer $token")
            }.apply { resultBlock(response.status(), response.content) }
        }
    }

    fun TestApplicationEngine.renameBuildVersion(
        agentId: String,
        token: String = globToken,
        payload: AgentBuildVersionJson,
        resultBlock: suspend (HttpStatusCode?, String?) -> Unit = { _, _ -> }
    ) {
        callAsync {
            handleRequest(
                HttpMethod.Post,
                "/api" + application.locations.href(Routes.Api.Agent.RenameBuildVersion(agentId))
            ) {
                addHeader(HttpHeaders.Authorization, "Bearer $token")
                setBody(AgentBuildVersionJson.serializer() stringify payload)
            }.apply { resultBlock(response.status(), response.content) }
        }
    }

    fun TestApplicationEngine.changePackages(
        agentId: String,
        token: String = globToken,
        payload: SystemSettingsDto,
        resultBlock: suspend (HttpStatusCode?, String?) -> Unit = { _, _ -> }
    ) = callAsync {
        handleRequest(HttpMethod.Post, "/api" + application.locations.href(Routes.Api.Agent.SystemSettings(agentId))) {
            addHeader(HttpHeaders.Authorization, "Bearer $token")
            setBody(SystemSettingsDto.serializer() stringify payload)
        }.apply { resultBlock(response.status(), response.content) }
    }

}

data class AgentKey(val id: String, val instanceId: String)
data class AgentStruct(
    val agWrap: AgentWrap,
    val startClb: suspend () -> Unit = {},
    val clbs: suspend TestApplicationEngine.(AdminUiChannels, Agent) -> Unit,
    val reconnects: MutableList<Pair<AgentWrap, suspend TestApplicationEngine.(AdminUiChannels, Agent) -> Unit>>
)

data class AgentWrap(
    val id: String,
    val instanceId: String = id + "1",
    val buildVersion: String = "0.1.0",
    val serviceGroupId: String = "",
    val needSync: Boolean = true,
    val agentType: AgentType = AgentType.JAVA
)
