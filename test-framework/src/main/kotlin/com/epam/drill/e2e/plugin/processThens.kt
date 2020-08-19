@file:Suppress("UNCHECKED_CAST")

package com.epam.drill.e2e.plugin

import com.epam.drill.common.*
import com.epam.drill.e2e.*
import com.epam.drill.testdata.*
import kotlinx.coroutines.*
import org.apache.bcel.classfile.*
import java.io.*
import kotlin.test.*

inline fun <reified X : PluginStreams> AdminTest.processThens(
    thens: MutableList<ThenAgentAsyncStruct>,
    pluginId: String,
    agentStreamDebug: Boolean,
    ui: AdminUiChannels,
    pluginMeta: PluginMetadata,
    globLaunch: Job
) {
    thens.forEach { (ag, build, it) ->
        val classes = File("./build/classes/java/${build.name}")
            .walkTopDown()
            .filter { it.extension == "class" }
            .toList()
            .toTypedArray()
        engine.handleWebSocketConversation("/ws/plugins/${testPlugin.pluginId}?token=${globToken}") { uiIncoming, ut ->
            val st = X::class.java.constructors.single().newInstance() as PluginStreams
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
            with(st) { queued(uiIncoming, ut) }
            engine.handleWebSocketConversation(
                "/agent/attach",
                wsRequestRequiredParams(ag)
            ) { inp, out ->
                val apply =
                    Agent(
                        application,
                        ag.id,
                        inp,
                        out,
                        agentStreamDebug
                    ).apply { queued() }
                apply.getHeaders()
                //
                apply.`get-set-packages-prefixes`()
                val bcelClasses = classes.map {
                    it.inputStream().use { fs -> ClassParser(fs, "").parse() }
                }
                val classMap: Map<String, ByteArray> = bcelClasses.associate {
                    it.className.replace(".", "/") to it.bytes
                }
                callAsync(asyncEngine.context) {
                    loadPlugin(
                        apply,
                        ag,
                        classMap,
                        pluginId,
                        agentStreamDebug,
                        out,
                        st,
                        pluginTestInfo,
                        pluginMeta,
                        build,
                        true
                    )
                }
                assertEquals(null, ui.getAgent()?.status)
                assertEquals(AgentStatus.ONLINE, ui.getAgent()?.status)
                assertEquals(AgentStatus.BUSY, ui.getAgent()?.status)
                ui.getAgent()
                it(pluginTestInfo, st, build)
                while (globLaunch.isActive)
                    delay(100)
            }
        }
    }
}
