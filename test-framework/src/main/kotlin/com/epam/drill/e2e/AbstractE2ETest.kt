package com.epam.drill.e2e

import com.epam.drill.common.*
import com.epam.drill.endpoints.*
import com.epam.drill.endpoints.agent.*
import com.epam.drill.router.*
import com.epam.drill.testdata.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.io.*
import java.io.*
import java.util.concurrent.*


abstract class AbstractE2ETest {
    @TempDir
    lateinit var projectDir: File


    lateinit var globToken: String
    val agents =
        ConcurrentHashMap<String, Triple<AgentWrap, suspend TestApplicationEngine.(AdminUiChannels, Agent) -> Unit, MutableList<Pair<AgentWrap, suspend TestApplicationEngine.(AdminUiChannels, Agent) -> Unit>>>>()


    fun createSimpleAppWithUIConnection(
        uiStreamDebug: Boolean = false,
        agentStreamDebug: Boolean = false,
        block: suspend () -> Unit
    ) {
        val appConfig = AppConfig(projectDir)
        val testApp = appConfig.testApp
        var coroutineException: Throwable? = null
        val handler = CoroutineExceptionHandler { _, exception ->
            coroutineException = exception
        }
        withTestApplication({ testApp(this, sslPort) }) {
            globToken = requestToken()
            //create the 'drill-admin-socket' websocket connection
            handleWebSocketConversation("/ws/drill-admin-socket?token=${globToken}") { uiIncoming, ut ->

                block()
                val cs = mutableMapOf<String, AdminUiChannels>()
                runBlocking {
                    agents.map { (_, xx) ->
                        val (ag, connect, thens) = xx
                        launch(handler) {
                            val ui = AdminUiChannels()
                            cs[ag.id] = ui
                            val uiE = UIEVENTLOOP(cs, uiStreamDebug)
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
                                val apply = Agent(application, ag.id, inp, out, agentStreamDebug).apply { queued() }
                                connect(
                                    this@withTestApplication,
                                    ui,
                                    apply
                                )
                            }
                            thens.forEach { (ain, it) ->

                                handleWebSocketConversation(
                                    "/agent/attach",
                                    wsRequestRequiredParams(ain)
                                ) { inp, out ->
                                    ui.getAgent()
                                    ui.getBuilds()
                                    val apply =
                                        Agent(application, ain.id, inp, out, agentStreamDebug).apply { queued() }
                                    it(
                                        this@withTestApplication,
                                        ui,
                                        apply
                                    )
                                }
                            }
                        }
                    }.forEach { it.join() }
                }

            }
            if (coroutineException != null) {
                throw coroutineException as Throwable
            }
        }
    }


    fun connectAgent(
        ags: AgentWrap,
        bl: suspend TestApplicationEngine.(AdminUiChannels, Agent) -> Unit
    ): AbstractE2ETest {
        agents[ags.id] = Triple(ags, bl, mutableListOf())
        return this
    }


    fun newConnect(
        ags: AgentWrap,
        bl: suspend TestApplicationEngine.(AdminUiChannels, Agent) -> Unit
    ): AbstractE2ETest {
        agents[ags.id]?.third?.add(ags to bl)
        return this
    }


    fun TestApplicationEngine.register(
        agentId: String,
        token: String = globToken,
        payload: AgentRegistrationInfo = AgentRegistrationInfo("xz", "ad", "sad")
    ) =
        handleRequest(HttpMethod.Post, "/api" + application.locations.href(Routes.Api.Agent.RegisterAgent(agentId))) {
            addHeader(HttpHeaders.Authorization, "Bearer $token")
            setBody(AgentRegistrationInfo.serializer() stringify payload)
        }.run { response.status() to response.content }

    fun TestApplicationEngine.addPlugin(agentId: String, payload: PluginId, token: String = globToken) =
        handleRequest(HttpMethod.Post, "/api" + application.locations.href(Routes.Api.Agent.AddNewPlugin(agentId))) {
            addHeader(HttpHeaders.Authorization, "Bearer $token")
            setBody(PluginId.serializer() stringify payload)
        }.run { response.status() to response.content }

    fun TestApplicationEngine.unRegister(agentId: String, token: String = globToken) =
        handleRequest(HttpMethod.Post, "/api" + application.locations.href(Routes.Api.Agent.UnregisterAgent(agentId))) {
            addHeader(HttpHeaders.Authorization, "Bearer $token")
        }.run { response.status() to response.content }

    fun TestApplicationEngine.unLoadPlugin(agentId: String, payload: PluginId, token: String = globToken) {
        handleRequest(
            HttpMethod.Post,
            "/api" + application.locations.href(Routes.Api.Agent.UnloadPlugin(agentId, payload.pluginId))
        ) {
            addHeader(HttpHeaders.Authorization, "Bearer $token")
        }.run { response.status() to response.content }
    }

    fun TestApplicationEngine.togglePlugin(agentId: String, pluginId: PluginId, token: String = globToken) {
        handleRequest(
            HttpMethod.Post,
            "/api" + application.locations.href(Routes.Api.Agent.TogglePlugin(agentId, pluginId.pluginId))
        ) {
            addHeader(HttpHeaders.Authorization, "Bearer $token")
        }.run { response.status() to response.content }
    }

    fun TestApplicationEngine.toggleAgent(agentId: String, token: String = globToken) {
        handleRequest(
            HttpMethod.Post,
            "/api" + application.locations.href(Routes.Api.Agent.AgentToggleStandby(agentId))
        ) {
            addHeader(HttpHeaders.Authorization, "Bearer $token")
        }.run { response.status() to response.content }
    }

    fun TestApplicationEngine.renameBuildVersion(
        agentId: String,
        token: String = globToken,
        payload: AgentBuildVersionJson
    ) {
        handleRequest(
            HttpMethod.Post,
            "/api" + application.locations.href(Routes.Api.Agent.RenameBuildVersion(agentId))
        ) {
            addHeader(HttpHeaders.Authorization, "Bearer $token")
            setBody(AgentBuildVersionJson.serializer() stringify payload)
        }.run { response.status() to response.content }
    }

}

data class AgentWrap(val id: String, val buildVersion: String = "0.1.0", val needSync: Boolean = true)