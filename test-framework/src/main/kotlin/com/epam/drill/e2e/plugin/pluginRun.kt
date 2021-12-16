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
package com.epam.drill.e2e.plugin

import com.epam.drill.admin.api.group.*
import com.epam.drill.admin.common.*
import com.epam.drill.common.*
import com.epam.drill.e2e.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.testdata.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.json.*
import java.io.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.reflect.*

fun <PS : PluginStreams> E2EPluginTest.pluginRun(
    psClass: KClass<PS>,
    block: suspend TestContext<PS>.() -> Unit,
    uiStreamDebug: Boolean,
    agentStreamDebug: Boolean,
    context: CompletableJob,
) {
    val appConfig = AppConfig(projectDir)
    val testApp = appConfig.testApp
    var coroutineException: Throwable? = null
    val handler = CoroutineExceptionHandler { _, exception -> coroutineException = exception } + context
    withApplication(
        environment = createTestEnvironment { parentCoroutineContext = context },
        configure = { dispatcher = Dispatchers.IO + context })
    {
        asyncEngine = AsyncTestAppEngine(handler, this)
        testApp(application, sslPort)
        storeManager = appConfig.storeManager
        commonStore = appConfig.commonStore
        globToken = requestToken()

        handleWebSocketConversation("/ws/drill-admin-socket?token=${globToken}") { frontIn, uts ->
            val cont = TestContext<PS>()
            block(cont)
            Subscribe("/agents")
            uts.send(uiMessage(Subscribe("/agents")))
            frontIn.receive()
            val cs = mutableMapOf<String, AdminUiChannels>()
            val glob = Channel<GroupedAgentsDto>()
            val globLaunch = application.launch(handler) {
                watcher?.invoke(asyncEngine, glob)
            }

            val nonStrictJson = Json { ignoreUnknownKeys = true }
            val pluginMeta = nonStrictJson.decodeFromString(
                PluginMetadata.serializer(),
                File(System.getProperty("plugin.config.path") ?: "./../plugin_config.json").readText()
            )
            val pluginId = pluginMeta.id
            coroutineScope {
                cont.agents.map { (_, agentAsyncStruct) ->
                    val (ag, build, connect, reconnectionCallbacks) = agentAsyncStruct
                    val ui = AdminUiChannels()
                    cs[ag.id] = ui
                    with(UIEVENTLOOP(cs, uiStreamDebug, glob)) { application.queued(appConfig.wsTopic, frontIn) }
                    uts.send(uiMessage(Subscribe("/agents/${ag.id}")))
                    launch(handler) {
                        processFirstConnect(
                            psClass,
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

                        processThens(
                            psClass,
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
