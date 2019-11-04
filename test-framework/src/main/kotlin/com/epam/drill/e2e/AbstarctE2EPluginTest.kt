@file:Suppress("UNCHECKED_CAST", "unused", "UNUSED_PARAMETER")

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
import org.apache.commons.codec.digest.*
import org.junit.*
import org.junit.rules.*
import java.io.*
import java.util.concurrent.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

abstract class AbstarctE2EPluginTest<T : PluginStreams> {

    @get:Rule
    val projectDir = TemporaryFolder()
    val appConfig = AppConfig(projectDir)
    val testApp = appConfig.testApp
    lateinit var globToken: String
    val agents = ConcurrentHashMap<String, AgentAsyncStruct<T>>()


    inline fun <reified X : PluginStreams> createSimpleAppWithPlugin(
        uiStreamDebug: Boolean = false,
        agentStreamDebug: Boolean = false,
        noinline block: suspend () -> Unit
    ) {
        var coroutineException: Throwable? = null
        val handler = CoroutineExceptionHandler { _, exception ->
            coroutineException = exception
        }
        withTestApplication({ testApp(this, sslPort) }) {
            globToken = requestToken()
            //create the 'drill-admin-socket' websocket connection


            runBlocking {
                block()
                val pluginId = (PluginMetadata.serializer() parse File(
                    System.getProperty("plugin.config.path") ?: "plugin_config.json"
                ).readText()).id
                agents.map { (_, xx) ->
                    val (ag, classes, connect, thens) = xx



                    launch(handler) {
                        handleWebSocketConversation("/ws/drill-plugin-socket?token=${globToken}") { uiIncoming, ut ->
                            val single = X::class.java.constructors.single()
                            val st = single.newInstance() as T
                            val pluginTestInfo = PluginTestContext(
                                ag.id, pluginId, ag.buildVersion, globToken, classes.size, this@withTestApplication
                            )
                            st.info = pluginTestInfo
                            st.app = application
                            with(st) { queued(uiIncoming, ut) }


                            delay(50)

                            handleWebSocketConversation(
                                "/agent/attach",
                                wsRequestRequiredParams(ag)
                            ) { inp, out ->
                                val apply = Agent(application, ag.id, inp, out, agentStreamDebug).apply { queued() }
                                apply.getServiceConfig()?.sslPort
                                register(ag.id)
                                apply.`get-set-packages-prefixes`()
                                apply.`get-load-classes-data`(*classes.toTypedArray())
                                delay(2000)
                                addPlugin(ag.id, PluginId(pluginId))

                                apply.getLoadedPlugin { _, file ->
                                    DigestUtils.md5Hex(file)
                                }

                                connect(
                                    pluginTestInfo,
                                    st,
                                    apply
                                )
                            }
                        }
                        thens.forEach { (ain, classes, it) ->
                            handleWebSocketConversation("/ws/drill-plugin-socket?token=${globToken}") { uiIncoming, ut ->
                                val single = X::class.java.constructors.single()
                                val st = single.newInstance() as T
                                val pluginTestInfo = PluginTestContext(
                                    ain.id,
                                    pluginId,
                                    ain.buildVersion,
                                    globToken,
                                    classes.size,
                                    this@withTestApplication
                                )
                                st.info = pluginTestInfo
                                st.app = application
                                with(st) { queued(uiIncoming, ut) }

                                handleWebSocketConversation(
                                    "/agent/attach",
                                    wsRequestRequiredParams(ain)
                                ) { inp, out ->
                                    val apply =
                                        Agent(application, ain.id, inp, out, agentStreamDebug).apply { queued() }
                                    apply.getServiceConfig()?.sslPort
                                    apply.`get-set-packages-prefixes`()
                                    apply.`get-load-classes-data`(*classes.toTypedArray())
                                    apply.getLoadedPlugin { _, file ->
                                        DigestUtils.md5Hex(file)
                                    }

                                    it(
                                        pluginTestInfo,
                                        st,
                                        apply
                                    )
                                }
                            }
                        }
                    }
                }.forEach { it.join() }
            }

            if (coroutineException != null) {
                throw coroutineException as Throwable
            }
        }
    }


    fun connectAgent(
        classes: Set<String>,
        ags: AgentWrap = AgentWrap("agent-1",
            run {
                val map = classes.map {
                    this::class.java.getResourceAsStream("/classes/$it").readBytes().sum()
                }
                map.sum().toString()
            }


        ),
        bl: suspend PluginTestContext.(T, Agent) -> Unit
    ): AbstarctE2EPluginTest<T> {
        agents[ags.id] = AgentAsyncStruct(ags, classes, bl)
        return this
    }

    data class AgentAsyncStruct<T>(
        val ag: AgentWrap,
        val classes: Set<String>,
        val callback: suspend PluginTestContext.(T, Agent) -> Unit,
        val thenCallbacks: MutableList<ThenAgentAsyncStruct<T>> = mutableListOf()
    )


    data class ThenAgentAsyncStruct<T>(
        val ag: AgentWrap,
        val classes: Set<String>,
        val callback: suspend PluginTestContext.(T, Agent) -> Unit
    )

    fun newConnect(
        classes: Set<String>,
        ags: AgentWrap = AgentWrap("agent-1",
            run {
                val map = classes.map {
                    this::class.java.getResourceAsStream("/classes/$it").readBytes().sum()
                }
                map.sum().toString()
            }


        ),
        bl: suspend PluginTestContext.(T, Agent) -> Unit
    ): AbstarctE2EPluginTest<T> {
        agents[ags.id]?.thenCallbacks?.add(ThenAgentAsyncStruct(ags, classes, bl))
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

data class PluginTestContext(
    val agentId: String,
    val pluginId: String,
    val buildVersionHash: String,
    val token: String,
    val classesCount: Int,
    val engine: TestApplicationEngine
) {

    fun pluginAction(
        payload: String,
        pluginId: String = this.pluginId,
        agentId: String = this.agentId,
        token: String = this.token

    ) = engine.handleRequest(
        HttpMethod.Post,
        "/api" + engine.application.locations.href(Routes.Api.Agent.DispatchPluginAction(agentId, pluginId))
    ) {
        addHeader(HttpHeaders.Authorization, "Bearer $token")
        setBody(payload)
    }.run { response.status() to response.content }

}


