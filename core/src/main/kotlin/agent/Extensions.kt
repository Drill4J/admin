package com.epam.drill.admin.agent

import com.epam.drill.admin.endpoints.*
import com.epam.drill.common.*

fun Iterable<AgentInfo>.plugins(): List<PluginMetadata> {
    return flatMap(AgentInfo::plugins).distinctBy(PluginMetadata::id)
}

fun Iterable<AgentInfo>.byPluginId(pluginId: String): List<AgentInfo> = filter { agentInfo ->
    agentInfo.plugins.any { plugin -> plugin.id == pluginId }
}

fun Iterable<AgentInfo>.mapToDto(agentManager: AgentManager) = map { it.toDto(agentManager) }
