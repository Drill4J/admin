@file:Suppress("UNCHECKED_CAST")

package com.epam.drill.e2e.plugin

import com.epam.drill.*
import com.epam.drill.builds.*
import com.epam.drill.common.*
import com.epam.drill.e2e.*
import com.epam.drill.endpoints.*
import com.epam.drill.endpoints.plugin.*
import com.epam.drill.plugin.api.*
import com.epam.drill.plugin.api.message.*
import com.epam.drill.plugin.api.processing.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.*
import org.apache.bcel.classfile.*
import org.apache.commons.codec.digest.*
import java.io.*
import java.util.jar.*
import kotlin.test.*

inline fun <reified X : PluginStreams> processThens(
    thens: MutableList<ThenAgentAsyncStruct>,
    testApplicationEngine: TestApplicationEngine,
    e2EPluginTest: E2EPluginTest,
    pluginId: String,
    agentStreamDebug: Boolean,
    ui: AdminUiChannels,
    pluginMeta: PluginMetadata,
    globLaunch: Job
) {
    thens.forEach { (ain, build, it) ->
        val classes = File("./build/classes/java/${build.name}")
            .walkTopDown()
            .filter { it.extension == "class" }
            .toList()
            .toTypedArray()
        testApplicationEngine.handleWebSocketConversation("/ws/drill-plugin-socket?token=${e2EPluginTest.globToken}") { uiIncoming, ut ->
            val st = X::class.java.constructors.single().newInstance() as PluginStreams
            val pluginTestInfo = PluginTestContext(
                ain.id,
                pluginId,
                ain.buildVersion,
                e2EPluginTest.globToken,
                classes.size,
                testApplicationEngine
            )
            st.info = pluginTestInfo
            st.app = application
            with(st) { queued(uiIncoming, ut) }

            testApplicationEngine.handleWebSocketConversation(
                "/agent/attach",
                wsRequestRequiredParams(ain)
            ) { inp, out ->
                val apply =
                    Agent(
                        application,
                        ain.id,
                        inp,
                        out,
                        agentStreamDebug
                    ).apply { queued() }
                apply.getServiceConfig()?.sslPort
                //
                apply.`get-set-packages-prefixes`()
                val bcelClasses = classes.map {
                    it.inputStream().use { fs -> ClassParser(fs, "").parse() }
                }

                apply.`get-load-classes-data`(*bcelClasses.toTypedArray())
                val classMap: Map<String, ByteArray> = bcelClasses.associate {
                    it.className.replace(".", "/") to it.bytes
                }
                assertEquals(AgentStatus.BUSY, ui.getAgent()?.status)


                lateinit var bs: ByteArray
                apply.getLoadedPlugin { _, file ->
                    DigestUtils.md5Hex(file)
                    bs = file
                }
                assertEquals(AgentStatus.ONLINE, ui.getAgent()?.status)
                val async = application.async(Dispatchers.IO) {
                    val jarInputStream = JarInputStream(ByteArrayInputStream(bs))
                    val sequence = sequence {
                        var nextJarEntry = jarInputStream.nextJarEntry
                        do {
                            yield(nextJarEntry)
                            nextJarEntry = jarInputStream.nextJarEntry
                        } while (nextJarEntry != null)
                    }

                    val clazz = retrieveApiClass(
                        AgentPart::class.java, sequence.toSet(),
                        ClassLoader.getSystemClassLoader()
                    )!! as Class<AgentPart<*, *>>
                    jarInputStream.close()
                    clazz
                }


                val agentData = AgentDatum(classMap)
                val declaredConstructor =
                    async.await().getDeclaredConstructor(PluginPayload::class.java)
                val agentPart = declaredConstructor.newInstance(
                    PluginPayload(pluginId, agentData)
                ) as AgentPart<*, *>
                val spykAgentPart = spyk(agentPart)
                val memoryClassLoader = MemoryClassLoader()
                if (spykAgentPart is InstrumentationPlugin)
                    every { spykAgentPart.retransform() } answers {
                        agentData.classMap.forEach { (k, v) ->
                            val clsName = k.replace("/", ".")
                            val instrument = spykAgentPart.instrument(k, v)
                            memoryClassLoader.addDefinition(clsName, instrument!!)
                            memoryClassLoader.loadClass(clsName)
                        }

                    }
                apply.plugin = spykAgentPart
                every { spykAgentPart.send(any()) } coAnswers {
                    if (agentStreamDebug)
                        println(this.args[0])
                    val content: String = this.args[0].toString()
                    out.send(
                        AgentMessage(
                            MessageType.PLUGIN_DATA, "",
                            MessageWrapper.serializer() stringify MessageWrapper(
                                spykAgentPart.id,
                                DrillMessage("test", content)
                            )
                        )
                    )
                }

                st.subscribe(
                    SubscribeInfo(
                        pluginTestInfo.agentId,
                        pluginTestInfo.buildVersionHash
                    )
                )

                spykAgentPart.enabled = true
                spykAgentPart.updateRawConfig(pluginMeta.config)
                spykAgentPart.on()

                pluginTestInfo.lis = memoryClassLoader.sw
                val first = memoryClassLoader.sw.first {
                    !it.isInterface &&
                            it.interfaces.flatMap { it.interfaces.toSet() }.any { it == Tst::class.java }
                }
                mockkObject(DrillContext)
                every { DrillContext[any()] } returns ""
                every { DrillContext.invoke() } returns null
                build.test = first.newInstance() as Tst
                unmockkObject(DrillContext)

                it(
                    pluginTestInfo,
                    st,
                    build
                )
                while (globLaunch.isActive)
                    delay(100)
            }
        }
    }
}

