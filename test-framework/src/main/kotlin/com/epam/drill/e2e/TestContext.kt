package com.epam.drill.e2e

import com.epam.drill.builds.*
import java.util.*
import java.util.concurrent.*

class TestContext<T : PluginStreams>(val agents: MutableMap<String, AgentAsyncStruct> = ConcurrentHashMap()) {

    inline fun <reified B : Build> connectAgent(
        serviceGroup: String = "",
        ags: AgentWrap = AgentWrap(
            id = UUID.randomUUID().toString().replace("-", ""),
            instanceId = "1",
            buildVersion = B::class.objectInstance!!.version,
            needSync = true,
            serviceGroupId = serviceGroup
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
        serviceGroup: String = "",
        ags: AgentWrap = AgentWrap(
            id = agents.keys.first(),
            instanceId = "1",
            buildVersion = B::class.objectInstance!!.version,
            serviceGroupId = serviceGroup
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
