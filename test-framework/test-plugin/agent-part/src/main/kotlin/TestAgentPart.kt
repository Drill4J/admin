package com.epam.drill.plugins.coverage

import com.epam.drill.plugin.api.*
import com.epam.drill.plugin.api.processing.*

@Suppress("unused")
class TestAgentPart constructor(
    private val payload: PluginPayload
) : AgentPart<String, String>(payload) {
    override fun on() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun off() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override val confSerializer: kotlinx.serialization.KSerializer<String>
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override fun initPlugin() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun destroyPlugin(unloadReason: UnloadReason) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override val id: String
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
    override val serDe: SerDe<String>
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override suspend fun doAction(action: String): Any {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}