package com.epam.drill.admin.endpoints

import com.epam.drill.common.*
import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.plugin.api.end.*

data class AgentEntry(
    var agent: AgentInfo,
    val agentSession: AgentWsSession,
    val instance: MutableMap<String, AdminPluginPart<*>> = mutableMapOf()
)
