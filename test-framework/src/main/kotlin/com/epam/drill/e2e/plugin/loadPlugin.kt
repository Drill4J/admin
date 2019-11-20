package com.epam.drill.e2e.plugin

import com.epam.drill.builds.*
import com.epam.drill.common.*
import com.epam.drill.e2e.*
import com.epam.drill.endpoints.*
import com.epam.drill.endpoints.plugin.*
import com.epam.drill.plugin.api.*
import com.epam.drill.plugin.api.message.*
import com.epam.drill.plugin.api.processing.*
import io.ktor.http.cio.websocket.*
import io.mockk.*
import kotlinx.coroutines.channels.*
import org.apache.commons.codec.digest.*
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
    agentStreamer.getLoadedPlugin { _, file ->
        DigestUtils.md5Hex(file)
        bs = file

        val memoryClassLoader = MemoryClassLoader()

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
        val clazz = memoryClassLoader.clazz(
            if (random) ag.id + UUID.randomUUID().toString().substring(0..3) else ag.id,
            toSet,
            jarFile
        )
        jarInputStream.close()


        val agentData = AgentDatum(classMap)
        val declaredConstructor =
            clazz.getDeclaredConstructor(memoryClassLoader.loadClass(PluginPayload::class.java.name))
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
        agentStreamer.plugin = spykAgentPart

        every {
            spykAgentPart.send(any())
        } coAnswers {
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
            ),
            "new-destination"
        )
        st.subscribe(
            SubscribeInfo(
                pluginTestInfo.agentId,
                pluginTestInfo.buildVersionHash
            ),
            "/packagesChangesCount"
        )

        spykAgentPart.enabled = true
        spykAgentPart.updateRawConfig(pluginMeta.config)
        spykAgentPart.on()

        pluginTestInfo.lis = memoryClassLoader.sw
        val first = memoryClassLoader.sw.firstOrNull {
            !it.isInterface &&
                    it.interfaces.flatMap { it.interfaces.toSet() }.any { it == Tst::class.java }
        } ?: fail("can't find classes for build")
        @Suppress("UNCHECKED_CAST")
        build.test = first as Class<Tst>
        mut.unlock()
    }
}