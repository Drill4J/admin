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
    version = version
)

internal fun Iterable<Plugin>.mapToDto() = map(Plugin::toDto)

internal fun Iterable<Plugin>.mapToDto(
    agents: Iterable<AgentInfo>
): List<PluginDto> = map { pb ->
    //TODO remove
    pb.toDto().copy(installedAgentsCount = agents.byPluginId(pb.pluginBean.id).count())
}
