package com.epam.drill.admin.agent.plugin

import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.api.*
import kotlinx.serialization.Serializable

@Serializable
@Topic("/plugin/state")
class PluginState

suspend fun AgentWsSession.syncPluginState() {
    sendToTopic<PluginState, String>("")
}
