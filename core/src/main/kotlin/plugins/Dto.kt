package com.epam.drill.admin.plugins

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.api.plugin.*

internal fun Plugin.toDto() = PluginDto(
    id = pluginBean.id,
    name = pluginBean.name,
    description = pluginBean.description,
    version = version
)

internal fun Iterable<Plugin>.mapToDto() = map(Plugin::toDto)

internal fun Iterable<Plugin>.mapToDto(
    agents: Iterable<AgentInfo>
): List<PluginDto> = mapToDto().map { plugin ->
    val available = agents.any { plugin.id !in it.plugins }
    plugin.copy(
        available = available,
        relation = "Installed".takeIf { !available }.orEmpty() //TODO remove
    )
}
