package com.epam.drill.admin.plugins

import com.epam.drill.admin.agent.*
import com.epam.drill.common.*
import kotlinx.serialization.*

@Serializable
data class PluginDto(
    var id: String,
    var name: String = "",
    var description: String = "",
    var type: String = "",
    var status: Boolean? = true,
    var config: String? = "",
    var installedAgentsCount: Int? = 0,
    val relation: String?,
    val version: String = ""
)

fun Plugin.toDto() = PluginDto(
    id = pluginBean.id,
    name = pluginBean.name,
    description = pluginBean.description,
    type = pluginBean.type,
    status = pluginBean.enabled,
    config = pluginBean.config,
    installedAgentsCount = 0,
    relation = null,
    version = version
)

fun Iterable<Plugin>.mapToDto() = map(Plugin::toDto)

fun Iterable<Plugin>.mapToDto(agents: Iterable<AgentInfo>) = map { pb ->
    return@map pb.toDto().apply {
        config = null
        status = null
        installedAgentsCount = agents.byPluginId(id).count()
    }
}
