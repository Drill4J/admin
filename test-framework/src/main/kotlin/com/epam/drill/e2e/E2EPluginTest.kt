@file:Suppress("UNCHECKED_CAST", "unused", "UNUSED_PARAMETER", "DEPRECATION")

package com.epam.drill.e2e

import com.epam.drill.*
import com.epam.drill.builds.*
import com.epam.drill.common.*
import com.epam.drill.endpoints.*
import com.epam.drill.endpoints.agent.*
import com.epam.drill.endpoints.plugin.*
import com.epam.drill.plugin.api.*
import com.epam.drill.plugin.api.message.*
import com.epam.drill.plugin.api.processing.*
import com.epam.drill.router.*
import com.epam.drill.testdata.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.*
import org.apache.bcel.classfile.*
import org.apache.commons.codec.digest.*
import java.io.*
import java.util.*
import java.util.concurrent.*
import java.util.jar.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.test.*

abstract class E2EPluginTest<T : PluginStreams> : AdminTest() {

    val agents = ConcurrentHashMap<String, AgentAsyncStruct<T>>()

    inline fun <reified X : PluginStreams> createSimpleAppWithPlugin(
        uiStreamDebug: Boolean = false,
        agentStreamDebug: Boolean = false,
        noinline block: suspend () -> Unit
    ) {
        val appConfig = AppConfig(projectDir)
        val testApp = appConfig.testApp
        var coroutineException: Throwable? = null
        val handler = CoroutineExceptionHandler { _, exception ->
            coroutineException = exception
        }
        var l = System.currentTimeMillis()
        withTestApplication({ testApp(this, sslPort, false) }) {
            println((System.currentTimeMillis() - l))
            storeManager = appConfig.storeManager
            globToken = requestToken()
            handleWebSocketConversation("/ws/drill-admin-socket?token=${globToken}") { frontIn, uts ->
                val cs = mutableMapOf<String, AdminUiChannels>()
                runBlocking {
                    block()
                    val pluginMeta = (PluginMetadata.serializer() parse File(
                        System.getProperty("plugin.config.path") ?: "./../plugin_config.json"
                    ).readText())
                    val pluginId = pluginMeta.id
                    agents.map { (_, agentAsyncStruct) ->
                        val (ag, buildw, connect, thens) = agentAsyncStruct

                        val ui = AdminUiChannels()
                        cs[ag.id] = ui
                        val uiE = UIEVENTLOOP(cs, uiStreamDebug)
                        with(uiE) { application.queued(appConfig.wsTopic, frontIn) }
                        uts.send(UiMessage(WsMessageType.SUBSCRIBE, "/get-agent/${ag.id}"))
                        launch(handler) {
                            handleWebSocketConversation("/ws/drill-plugin-socket?token=${globToken}") { uiIncoming, ut ->
                                val classes = File("./build/classes/java/${buildw.name}")
                                    .walkTopDown()
                                    .filter { it.extension == "class" }
                                    .toList()
                                    .toTypedArray()
                                ui.getAgent()

                                val st = X::class.java.constructors.single().newInstance() as T
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
                                    val apply = Agent(application, ag.id, inp, out, agentStreamDebug).apply { queued() }
                                    apply.getServiceConfig()?.sslPort
                                    register(ag.id)
                                    ui.getAgent()
                                    ui.getAgent()
                                    apply.`get-set-packages-prefixes`()
                                    val bcelClasses = classes.map {
                                        it.inputStream().use { fs -> ClassParser(fs, "").parse() }
                                    }
                                    l = System.currentTimeMillis()
                                    apply.`get-load-classes-data`(*bcelClasses.toTypedArray())
                                    val classMap: Map<String, ByteArray> = bcelClasses.associate {
                                        it.className.replace(".", "/") to it.bytes
                                    }
                                    assertEquals(ui.getAgent()?.status, AgentStatus.ONLINE)

                                    assertEquals(
                                        HttpStatusCode.OK,
                                        addPlugin(ag.id, PluginId(pluginId)).first,
                                        "ADD PLUGIN"
                                    )

                                    lateinit var bs: ByteArray
                                    apply.getLoadedPlugin { _, file ->
                                        DigestUtils.md5Hex(file)
                                        bs = file
                                    }

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

                                    val memoryClassLoader = MemoryClassLoader()
                                    val agentData = AgentDatum(classMap)
                                    val declaredConstructor =
                                        async.await().getDeclaredConstructor(PluginPayload::class.java)
                                    val agentPart = declaredConstructor.newInstance(
                                        PluginPayload(pluginId, agentData)
                                    ) as AgentPart<*, *>
                                    println("API Loading: " + (System.currentTimeMillis() - l))
                                    l = System.currentTimeMillis()
                                    val spykAgentPart = spyk(agentPart)
                                    println("Mocking: " + (System.currentTimeMillis() - l))
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

                                    st.subscribe(SubscribeInfo(pluginTestInfo.agentId, pluginTestInfo.buildVersionHash))
                                    l = System.currentTimeMillis()
                                    spykAgentPart.enabled = true
                                    spykAgentPart.updateRawConfig(pluginMeta.config)
                                    spykAgentPart.on()
                                    println("ON: " + (System.currentTimeMillis() - l))
                                    pluginTestInfo.lis = memoryClassLoader.sw
                                    val first = memoryClassLoader.sw.first {
                                        !it.isInterface &&
                                                it.interfaces.flatMap { it.interfaces.toSet() }.any { it == Tst::class.java }
                                    }
                                    l = System.currentTimeMillis()
                                    mockkObject(DrillContext)
                                    every { DrillContext[any()] } returns ""
                                    every { DrillContext.invoke() } returns null
                                    buildw.test = first.newInstance() as Tst
                                    unmockkObject(DrillContext)
                                    println("GLO~bAL mocking: " + (System.currentTimeMillis() - l))
                                    connect(pluginTestInfo, st, buildw)
                                }
                            }

                            thens.forEach { (ain, build, it) ->
                                val classes = File("./build/classes/java/${build.name}")
                                    .walkTopDown()
                                    .filter { it.extension == "class" }
                                    .toList()
                                    .toTypedArray()
                                handleWebSocketConversation("/ws/drill-plugin-socket?token=${globToken}") { uiIncoming, ut ->
                                    val st = X::class.java.constructors.single().newInstance() as T
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


                                        val memoryClassLoader = MemoryClassLoader()
                                        val agentData = AgentDatum(classMap)
                                        val declaredConstructor =
                                            async.await().getDeclaredConstructor(PluginPayload::class.java)
                                        val agentPart = declaredConstructor.newInstance(
                                            PluginPayload(pluginId, agentData)
                                        ) as AgentPart<*, *>
                                        val spykAgentPart = spyk(agentPart)
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
                                    }
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


    inline fun <reified B : Build> connectAgent(
        ags: AgentWrap = AgentWrap(UUID.randomUUID().toString(),
            run {
                val map =
                    File("./build/classes/java/${B::class.objectInstance!!.name}").walkTopDown()
                        .filter { it.extension == "class" }.map {
                            it.readBytes().sum()
                        }
                map.sum().toString()
            }
        ),
        noinline bl: suspend PluginTestContext.(T, B) -> Unit
    ): E2EPluginTest<T> {

        val kClass = B::class
        val build = kClass.objectInstance!!
        agents[ags.id] = AgentAsyncStruct(
            ags,
            build,
            bl as suspend PluginTestContext.(T, Any) -> Unit
        )
        return this
    }

    data class AgentAsyncStruct<T>(
        val ag: AgentWrap,
        val build: Build,
        val callback: suspend PluginTestContext.(T, Any) -> Unit,
        val thenCallbacks: MutableList<ThenAgentAsyncStruct<T>> = mutableListOf()
    )


    data class ThenAgentAsyncStruct<T>(
        val ag: AgentWrap,
        val build: Build,
        val callback: suspend PluginTestContext.(T, Any) -> Unit
    )

    inline fun <reified B : Build> reconnect(
        ags: AgentWrap = AgentWrap(agents.keys.first(),
            run {
                val map =
                    File("./build/classes/java/${B::class.objectInstance!!.name}").walkTopDown()
                        .filter { it.extension == "class" }.map {
                            it.readBytes().sum()
                        }
                map.sum().toString()
            }
        ),
        noinline bl: suspend PluginTestContext.(T, B) -> Unit
    ): E2EPluginTest<T> {
        agents[ags.id]?.thenCallbacks?.add(
            ThenAgentAsyncStruct(
                ags,
                B::class.objectInstance!!,
                bl as suspend PluginTestContext.(T, Any) -> Unit
            )
        )
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
    val engine: TestApplicationEngine,
    var lis: MutableList<Class<*>> = mutableListOf()
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


class MemoryClassLoader : ClassLoader() {
    val sw: MutableList<Class<*>> = mutableListOf()
    private val definitions = mutableMapOf<String, ByteArray?>()

    fun addDefinition(name: String, bytes: ByteArray) {
        definitions[name] = bytes
    }

    override fun loadClass(name: String?, resolve: Boolean): Class<*> {
        val bytes = definitions[name]
        val clazz = if (bytes != null) {
            defineClass(name, bytes, 0, bytes.size)
        } else {
            super.loadClass(name, resolve)
        }
        sw.add(clazz)
        return clazz
    }
}

class AgentDatum(override val classMap: Map<String, ByteArray>) : AgentData


fun runWithSession(sessionId: String, block: () -> Unit) {
    mockkObject(DrillContext)
    every { DrillContext[any()] } returns "xxxx"
    every { DrillContext.invoke() } returns sessionId
    block()
    unmockkObject(DrillContext)
}