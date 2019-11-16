package com.epam.drill.e2e

import com.epam.drill.builds.*
import java.io.*
import java.util.*
import java.util.concurrent.*

class TestContext<T : PluginStreams>(val agents: MutableMap<String, AgentAsyncStruct> = ConcurrentHashMap()) {

    inline fun <reified B : Build> connectAgent(
        ags: AgentWrap = AgentWrap(UUID.randomUUID().toString().replace("-", ""),
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
    ): TestContext<T> {

        val kClass = B::class
        val build = kClass.objectInstance!!
        @Suppress("UNCHECKED_CAST")
        agents[ags.id] = AgentAsyncStruct(
            ags,
            build,
            bl as suspend PluginTestContext.(Any, Any) -> Unit
        )
        return this
    }

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
    ): TestContext<T> {
        @Suppress("UNCHECKED_CAST")
        agents[ags.id]?.thenCallbacks?.add(
            ThenAgentAsyncStruct(
                ags,
                B::class.objectInstance!!,
                bl as suspend PluginTestContext.(Any, Any) -> Unit
            )
        )
        return this
    }

}