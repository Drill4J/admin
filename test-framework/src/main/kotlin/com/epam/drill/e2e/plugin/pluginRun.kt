@file:Suppress("UNCHECKED_CAST")

package com.epam.drill.e2e.plugin

import com.epam.drill.agentmanager.*
import com.epam.drill.common.*
import com.epam.drill.e2e.*
import com.epam.drill.endpoints.*
import com.epam.drill.testdata.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.io.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

inline fun <reified PS : PluginStreams> E2EPluginTest.pluginRun(
    noinline block: suspend TestContext<PS>.() -> Unit,
    uiStreamDebug: Boolean,
    agentStreamDebug: Boolean
) {
    val appConfig = AppConfig(projectDir)
    val testApp = appConfig.testApp
    var coroutineException: Throwable? = null
    val handler = CoroutineExceptionHandler { _, exception -> coroutineException = exception }
    withTestApplication({ testApp(this, sslPort, false) }) {
        engine = this@withTestApplication
        storeManager = appConfig.storeManager
        globToken = requestToken()

        handleWebSocketConversation("/ws/drill-admin-socket?token=${globToken}") { frontIn, uts ->
            val cont = TestContext<PS>()
            block(cont)
            uts.send(UiMessage(WsMessageType.SUBSCRIBE, "/get-all-agents"))
            frontIn.receive()
            val cs = mutableMapOf<String, AdminUiChannels>()
            val glob = Channel<Set<AgentInfoWebSocket>>()
            val globLaunch = application.launch(handler) {
                watcher?.invoke(this@withTestApplication, glob)
            }
            val pluginMeta = (PluginMetadata.serializer() parse
                    File(System.getProperty("plugin.config.path") ?: "./../plugin_config.json").readText())
            val pluginId = pluginMeta.id
            coroutineScope {
                cont.agents.map { (_, agentAsyncStruct) ->
                    val (ag, build, connect, reconnectionCallbacks) = agentAsyncStruct
                    val ui = AdminUiChannels()
                    cs[ag.id] = ui
                    with(UIEVENTLOOP(cs, uiStreamDebug, glob)) { application.queued(appConfig.wsTopic, frontIn) }
                    uts.send(UiMessage(WsMessageType.SUBSCRIBE, "/get-agent/${ag.id}"))
                    launch(handler) {
                        processFirstConnect<PS>(
                            build,
                            ui,
                            ag,
                            pluginId,
                            uiStreamDebug,
                            agentStreamDebug,
                            pluginMeta,
                            connect,
                            globLaunch
                        )

                        processThens<PS>(
                            reconnectionCallbacks,
                            pluginId,
                            agentStreamDebug,
                            ui,
                            pluginMeta,
                            globLaunch
                        )
                    }
                }.forEach { it.join() }

                globLaunch.join()
            }
        }

        if (coroutineException != null) {
            throw coroutineException as Throwable
        }
    }
}
