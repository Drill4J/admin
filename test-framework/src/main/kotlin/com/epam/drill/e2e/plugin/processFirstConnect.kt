/**
 * Copyright 2020 - 2022 EPAM Systems
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

import com.epam.drill.admin.api.agent.*
import com.epam.drill.admin.api.routes.*
import com.epam.drill.admin.common.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.builds.*
import com.epam.drill.common.*
import com.epam.drill.e2e.*
import com.epam.drill.e2e.Agent
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import org.apache.bcel.classfile.*
import java.io.*
import kotlin.reflect.*

fun AdminTest.processFirstConnect(
    psClass: KClass<out PluginStreams>,
    build: Build,
    ui: AdminUiChannels,
    ag: AgentWrap,
    pluginId: String,
    uiStreamDebug: Boolean,
    agentStreamDebug: Boolean,
    pluginMeta: PluginMetadata,
    connect: suspend PluginTestContext.(Any, Any) -> Unit,
    globLaunch: Job,
    uts: SendChannel<Frame>
) {
    engine.handleWebSocketConversation("/ws/plugins/${pluginMeta.id}?token=${globToken}") { uiIncoming, ut ->
        val classes = File("./build/classes/java/${build.name}")
            .walkTopDown()
            .filter { it.extension == "class" }
            .toList()
            .toTypedArray()
        ui.getAgent()

        val st = psClass.java.constructors.single().newInstance() as PluginStreams
        val pluginTestInfo = PluginTestContext(
            ag.id,
            pluginId,
            ag.buildVersion,
            globToken,
            classes.size,
            engine,
            asyncEngine.context
        )
        st.info = pluginTestInfo
        st.app = engine.application
        with(st) { queued(uiIncoming, ut, uiStreamDebug) }
        delay(50)

        engine.handleWebSocketConversation(
            "/agent/attach",
            wsRequestRequiredParams(ag)
        ) { inp, out ->
            uts.send(uiMessage(Subscribe(application.toLocation(WsRoot.AgentBuild(ag.id, ag.buildVersion)))))
            val agent = Agent(
                engine.application,
                ag.id,
                inp,
                OutsSock(out, agentStreamDebug),
                agentStreamDebug
            ).apply { queued() }
            agent.getHeaders()
            asyncEngine.register(
                ag.id, payload = AgentRegistrationDto(
                    name = "xz",
                    description = "ad",
                    systemSettings = SystemSettingsDto(
                        packages = listOf("testPrefix")
                    ),
                    plugins = listOf(pluginMeta.id)
                )
            )
            agent.`get-set-packages-prefixes`()
            val bcelClasses = classes.map {
                it.inputStream().use { fs -> ClassParser(fs, "").parse() }
            }
            val classMap: Map<String, ByteArray> = bcelClasses.associate {
                it.className.replace(".", "/") to it.bytes
            }
            loadPlugin(
                agent,
                ag,
                classMap,
                pluginId,
                agentStreamDebug,
                out,
                st,
                pluginTestInfo,
                build
            )
            ui.getAgent()
            ui.getAgent()
            ui.getAgent()
            waitForBuildOnline(ui, build.version)
            connect(pluginTestInfo, st, build)
            while (globLaunch.isActive) {
                delay(100)
            }
        }
    }
}
