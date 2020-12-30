package com.epam.drill.e2e.plugin

import com.epam.drill.admin.api.websocket.*
import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.builds.*
import com.epam.drill.common.*
import com.epam.drill.e2e.*
import com.epam.drill.logger.api.*
import com.epam.drill.plugin.api.message.*
import com.epam.drill.plugin.api.processing.*
import io.ktor.http.cio.websocket.*
import io.ktor.util.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.io.*
import java.util.*
import java.util.jar.*
import kotlin.test.*

suspend fun AdminTest.loadPlugin(
    agentStreamer: Agent,
    ag: AgentWrap,
    classMap: Map<String, ByteArray>,
    pluginId: String,
    agentStreamDebug: Boolean,
    out: SendChannel<Frame>,
    st: PluginStreams,
    pluginTestInfo: PluginTestContext,
    pluginMeta: PluginMetadata,
    build: Build,
    random: Boolean = false
) {
    lateinit var bs: ByteArray
    agentStreamer.getLoadedPlugin { meta, file ->
        hex(file)
        bs = file

        val memoryClassLoader = MemoryClassLoader()

        val agentDir = projectDir.resolve(ag.id)
        agentDir.mkdirs()
        val agentJar = agentDir.resolve("ag-part.jar")
        agentJar.writeBytes(bs)

        val clazz = withContext(Dispatchers.IO) {
            JarFile(agentJar).use { jarFile ->
                JarInputStream(FileInputStream(agentJar)).use { jarInputStream ->
                    val sequence = sequence {
                        var nextJarEntry = jarInputStream.nextJarEntry
                        do {
                            yield(nextJarEntry)
                            nextJarEntry = jarInputStream.nextJarEntry
                        } while (nextJarEntry != null)
                    }

                    val toSet = sequence.toSet()
                    memoryClassLoader.clazz(
                        if (random) ag.id + UUID.randomUUID().toString().substring(0..3) else ag.id,
                        toSet,
                        jarFile
                    )
                }
            }
        }

        val agentData = AgentDatum(classMap)
        val declaredConstructor = clazz.getDeclaredConstructor(
            String::class.java,
            AgentContext::class.java,
            Sender::class.java,
            LoggerFactory::class.java
        )
        val sender = TestPluginSender(agentStreamDebug, out)
        val agentPart = declaredConstructor.newInstance(
            pluginId,
            testAgentContext,
            sender,
            SimpleLogging
        ) as AgentPart<*>
        val spykAgentPart = spyk(agentPart, ag.id)
        if (spykAgentPart is Instrumenter) {
            every { spykAgentPart["retransform"]() } answers {
                agentData.classMap.forEach { (name, bytes) ->
                    val clsName = name.replace("/", ".")
                    val clsBytes = bytes.takeIf { clsName.startsWith("com.epam.test") }?.let {
                        spykAgentPart.instrument(name, bytes)
                    } ?: bytes
                    memoryClassLoader.addDefinition(clsName, clsBytes)
                    memoryClassLoader.loadClass(clsName)
                }
            }
        } else {
            agentData.classMap.forEach { (k, v) ->
                val clsName = k.replace("/", ".")
                memoryClassLoader.addDefinition(clsName, v)
                memoryClassLoader.loadClass(clsName)
            }
        }
        agentStreamer.plugin = spykAgentPart

        st.initSubscriptions(
            AgentSubscription(
                pluginTestInfo.agentId,
                pluginTestInfo.buildVersionHash
            )
        )

        spykAgentPart.setEnabled(true)
        spykAgentPart.updateRawConfig(pluginMeta.config)
        spykAgentPart.initPlugin()
        agentStreamer.loaded(meta.id)

        val classes = classMap.map { ByteClass(it.key, it.value) }.toTypedArray()
        agentStreamer.`get-load-classes-data`(*classes)
        spykAgentPart.on()

        pluginTestInfo.lis = memoryClassLoader.sw
        val first = memoryClassLoader.sw.firstOrNull {
            !it.isInterface &&
                    it.interfaces.flatMap { it.interfaces.toSet() }.any { it == Tst::class.java }
        } ?: fail("can't find classes for build")
        @Suppress("UNCHECKED_CAST")
        build.test = first as Class<Tst>
    }
}

class TestPluginSender(
    private val trace: Boolean,
    private val out: SendChannel<Frame>
) : Sender {
    override fun send(pluginId: String, message: String) {
        runBlocking {
            if (trace) {
                println("Plugin $pluginId >> message:\n$message")
            }
            out.send(
                agentMessage(
                    MessageType.PLUGIN_DATA, "",
                    (MessageWrapper.serializer() stringify MessageWrapper(
                        pluginId,
                        DrillMessage(content = message)
                    )).encodeToByteArray()
                )
            )
        }
    }
}
