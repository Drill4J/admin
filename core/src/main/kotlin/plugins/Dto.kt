package com.epam.drill.admin.plugins

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.api.plugin.*

internal fun Plugin.toDto() = PluginDto(
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

internal fun Iterable<Plugin>.mapToDto() = map(Plugin::toDto)

internal fun Iterable<Plugin>.mapToDto(agents: Iterable<AgentInfo>) = map { pb ->
    return@map pb.toDto().apply {
        config = null
        status = null
        installedAgentsCount = agents.byPluginId(id).count()
    }
}
