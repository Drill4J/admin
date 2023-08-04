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

import com.epam.drill.admin.api.websocket.*
import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.plugins.coverage.TestAgentPart
import com.epam.drill.builds.*
import com.epam.drill.common.*
import com.epam.drill.common.classloading.EntitySource
import com.epam.drill.e2e.*
import com.epam.drill.e2e.Agent
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

suspend fun AdminTest.loadPlugin(
    agentStreamer: Agent,
    ag: AgentWrap,
    classMap: Map<String, ByteArray>,
    pluginId: String,
    agentStreamDebug: Boolean,
    out: SendChannel<Frame>,
    st: PluginStreams,
    pluginTestInfo: PluginTestContext,
    build: Build
) {
    agentStreamer.getLoadedPlugin { meta ->

        val memoryClassLoader = MemoryClassLoader()


        val clazz = TestAgentPart::class.java

        val agentData = AgentDatum(classMap)
        val declaredConstructor = clazz.getDeclaredConstructor(
            String::class.java,
            AgentContext::class.java,
            Sender::class.java
        )
        val sender = TestPluginSender(agentStreamDebug, out)
        val agentPart = declaredConstructor.newInstance(
            pluginId,
            testAgentContext,
            sender
        ) as AgentPart<*>
        this.agentPart = agentPart
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

        if (spykAgentPart is ClassScanner) {
            val capturedTransfer = slot<(Set<EntitySource>) -> Unit>()
            every { spykAgentPart.scanClasses(consumer = capture(capturedTransfer)) } answers {
                val transfer = capturedTransfer.captured
                transfer(agentData.classMap.map { TestClassSource(it.key, it.value) }.toSet())
            }
        }
        agentStreamer.plugin = spykAgentPart

        st.initSubscriptions(
            AgentSubscription(
                pluginTestInfo.agentId,
                pluginTestInfo.buildVersionHash
            )
        )

        spykAgentPart.load()
        spykAgentPart.on()

        pluginTestInfo.lis = memoryClassLoader.sw
        val buildClasses: Array<Class<*>> = memoryClassLoader.sw.filter {
            !it.isInterface && it.interfaces.flatMap { it.interfaces.toSet() }.any { it == Tst::class.java }
        }.toTypedArray()
        @Suppress("UNCHECKED_CAST")
        build.tests = buildClasses as Array<Class<Tst>>
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

class TestClassSource(private val className: String, private val bytes: ByteArray): EntitySource {
    override fun entityName() = className
    override fun bytes() = bytes
}
