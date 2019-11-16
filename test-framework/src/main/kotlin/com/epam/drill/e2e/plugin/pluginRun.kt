@file:Suppress("UNCHECKED_CAST")

package com.epam.drill.e2e.plugin

import com.epam.drill.agentmanager.*
import com.epam.drill.builds.*
import com.epam.drill.common.*
import com.epam.drill.e2e.*
import com.epam.drill.endpoints.*
import com.epam.drill.endpoints.plugin.*
import com.epam.drill.plugin.api.*
import com.epam.drill.plugin.api.message.*
import com.epam.drill.plugin.api.processing.*
import com.epam.drill.testdata.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import org.apache.bcel.classfile.*
import org.apache.commons.codec.digest.*
import java.io.*
import java.util.jar.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.test.*

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
                        handleWebSocketConversation("/ws/drill-plugin-socket?token=${globToken}") { uiIncoming, ut ->
                            val classes = File("./build/classes/java/${build.name}")
                                .walkTopDown()
                                .filter { it.extension == "class" }
                                .toList()
                                .toTypedArray()
                            ui.getAgent()

                            val st = PS::class.java.constructors.single().newInstance() as PluginStreams
                            val pluginTestInfo = PluginTestContext(
                                ag.id,
                                pluginId,
                                ag.buildVersion,
                                globToken,
                                classes.size,
                                this@withTestApplication
                            )
                            st.info = pluginTestInfo
                            st.app = application
                            with(st) { queued(uiIncoming, ut, uiStreamDebug) }
                            delay(50)

                            handleWebSocketConversation(
                                "/agent/attach",
                                wsRequestRequiredParams(ag)
                            ) { inp, out ->
                                val apply =
                                    Agent(application, ag.id, inp, out, agentStreamDebug).apply { queued() }
                                apply.getServiceConfig()?.sslPort
                                register(ag.id)
                                ui.getAgent()
                                ui.getAgent()
                                apply.`get-set-packages-prefixes`()
                                val bcelClasses = classes.map {
                                    it.inputStream().use { fs -> ClassParser(fs, "").parse() }
                                }
                                apply.`get-load-classes-data`(*bcelClasses.toTypedArray())
                                val classMap: Map<String, ByteArray> = bcelClasses.associate {
                                    it.className.replace(".", "/") to it.bytes
                                }
                                assertEquals(ui.getAgent()?.status, AgentStatus.ONLINE)

                                assertEquals(HttpStatusCode.OK, addPlugin(ag.id, PluginId(pluginId)).first, "CAN'T ADD THE PLUGIN")

                                lateinit var bs: ByteArray
                                apply.getLoadedPlugin { _, file ->
                                    DigestUtils.md5Hex(file)
                                    bs = file
                                }


                                val memoryClassLoader = MemoryClassLoader()

                                val async = application.async(Dispatchers.IO) {
                                    val resolve1 = projectDir.resolve(ag.id)
                                    resolve1.mkdirs()
                                    val resolve = resolve1.resolve("ag-part.jar")
                                    resolve.writeBytes(bs)
                                    val jarFile = JarFile(resolve)
                                    val jarInputStream = JarInputStream(FileInputStream(resolve))
                                    val sequence = sequence {
                                        var nextJarEntry = jarInputStream.nextJarEntry
                                        do {
                                            yield(nextJarEntry)
                                            nextJarEntry = jarInputStream.nextJarEntry
                                        } while (nextJarEntry != null)
                                    }
                                    val toSet = sequence.toSet()


                                    val clazz = memoryClassLoader.clazz(ag.id, toSet, jarFile)
                                    jarInputStream.close()
                                    clazz
                                }


                                val agentData = AgentDatum(classMap)
                                val await = async.await()
                                val declaredConstructor =
                                    await.getDeclaredConstructor(memoryClassLoader.loadClass(PluginPayload::class.java.name))
                                val pluginPayload = PluginPayload(pluginId, agentData)
                                val agentPart = declaredConstructor.newInstance(pluginPayload) as AgentPart<*, *>
                                mut.lock()
                                val spykAgentPart = spyk(agentPart, ag.id)
                                if (spykAgentPart is InstrumentationPlugin)
                                    every { spykAgentPart.retransform() } answers {
                                        agentData.classMap.forEach { (k, v) ->
                                            val clsName = k.replace("/", ".")
                                            val instrument = if (clsName.startsWith("com.epam.test"))
                                                spykAgentPart.instrument(k, v)
                                            else
                                                v
                                            memoryClassLoader.addDefinition(clsName, instrument!!)
                                            memoryClassLoader.loadClass(clsName)
                                        }

                                    }
                                else {
                                    agentData.classMap.forEach { (k, v) ->
                                        val clsName = k.replace("/", ".")
                                        memoryClassLoader.addDefinition(clsName, v)
                                        memoryClassLoader.loadClass(clsName)
                                    }
                                }
                                apply.plugin = spykAgentPart
                                every {
                                    spykAgentPart.send(any())
                                } coAnswers {
                                    println(self)
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
                                val first = memoryClassLoader.sw.firstOrNull {
                                    !it.isInterface &&
                                            it.interfaces.flatMap { it.interfaces.toSet() }.any { it == Tst::class.java }
                                } ?: fail("can't find classes for build")
                                mockkObject(DrillContext)
                                every { DrillContext[any()] } returns ""
                                every { DrillContext.invoke() } returns null
                                build.test = first.newInstance() as Tst
                                unmockkObject(DrillContext)
                                mut.unlock()
                                connect(pluginTestInfo, st, build)
                                while (globLaunch.isActive)
                                    delay(100)
                            }
                        }

                        processThens<PS>(
                            reconnectionCallbacks,
                            this@withTestApplication,
                            this@pluginRun,
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
