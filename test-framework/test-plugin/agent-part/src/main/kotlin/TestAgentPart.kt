package com.epam.drill.admin.plugins.coverage

import com.epam.drill.logger.api.*
import com.epam.drill.plugin.api.processing.*

@Suppress("unused")
class TestAgentPart constructor(
    id: String,
    agentContext: AgentContext,
    sender: Sender,
    logging: LoggerFactory
) : AgentPart<String>(id, agentContext, sender, logging) {

    override fun on() {
        send("xx")
    }

    override fun off() {
    }

    override fun initPlugin() {
        println("Plugin $id initialized.")
    }

    override fun destroyPlugin(unloadReason: UnloadReason) {
        TODO()
    }

    override suspend fun doAction(action: String): Any {
        println(action)
        return "action"
    }

    override fun parseAction(rawAction: String) = rawAction
}
