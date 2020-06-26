package com.epam.drill.admin.plugins.coverage

import com.epam.drill.plugin.api.*
import com.epam.drill.plugin.api.processing.*
import kotlinx.serialization.builtins.*

@Suppress("unused")
class TestAgentPart constructor(
    private val payload: PluginPayload
) : AgentPart<String, String>(payload) {
    override fun on() {
        send("xx")
    }

    override fun off() {
    }

    override val confSerializer: kotlinx.serialization.KSerializer<String>
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override fun initPlugin() {
        println("Plugin ${payload.pluginId} initialized.")
    }

    override fun destroyPlugin(unloadReason: UnloadReason) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override val id: String = payload.pluginId
    override val serDe: SerDe<String> = SerDe(String.serializer())

    override suspend fun doAction(action: String): Any {
        println(action)
        return "action"
    }
}
