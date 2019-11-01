package com.epam.drill.plugins

import com.epam.drill.common.*
import kotlinx.serialization.*

@Serializable
data class PluginWebSocket(
    var id: String,
    var name: String = "",
    var description: String = "",
    var type: String = "",
    var status: Boolean? = true,
    var config: String? = "",
    var installedAgentsCount: Int? = 0,
    var relation: String?
)

fun PluginMetadata.toPluginWebSocket() = PluginWebSocket(
    id = id,
    name = name,
    description = description,
    type = type,
    status = enabled,
    config = config,
    installedAgentsCount = 0,
    relation = null
)

fun MutableSet<PluginMetadata>.toPluginsWebSocket() = this.map { it.toPluginWebSocket() }.toMutableSet()

fun Collection<PluginMetadata>.toAllPluginsWebSocket(agents: Set<AgentInfo>?) = this.map { pb ->
    return@map pb.toPluginWebSocket().apply {
        config = null
        status = null
        installedAgentsCount = calculateInstalledAgentsCount(id, agents)
    }
}.toMutableList()

private fun calculateInstalledAgentsCount(id: String, agents: Set<AgentInfo>?): Int {
    return if (agents.isNullOrEmpty()) 0 else
        agents.count { agent -> agent.plugins.count { plugin -> plugin.id == id } > 0 }

}