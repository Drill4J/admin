package com.epam.drill.admin.agent

import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.common.*

fun Iterable<AgentInfo>.byPluginId(pluginId: String): List<AgentInfo> = filter { agentInfo ->
    agentInfo.plugins.any { plugin -> plugin.id == pluginId }
}

fun Iterable<AgentInfo>.mapToDto(agentManager: AgentManager) = map { it.toDto(agentManager) }

fun AgentManager.registered() = agentStorage.values.mapNotNull {
    it.agent.takeIf { it.status != AgentStatus.NOT_REGISTERED }
}.mapToDto(this)

fun Iterable<Plugin>.ofAgent(info: AgentInfo): List<Plugin> = run {
    val ids = info.plugins.mapTo(mutableSetOf()) { it.id }
    filter { it.pluginBean.id in ids }
}

fun Iterable<Plugin>.ofAgents(agents: Iterable<AgentInfo>): List<Plugin> = run {
    val ids = agents.plugins().mapTo(mutableSetOf()) { it.id }
    filter { it.pluginBean.id in ids }
}

fun AgentCreationDto.toAgentInfo(allPlugins: Plugins) = AgentInfo(
    id = id,
    agentType = agentType,
    name = name,
    status = AgentStatus.OFFLINE,
    serviceGroup = serviceGroup,
    environment = environment,
    description = description,
    agentVersion = "",
    buildVersion = "",
    plugins = allPlugins.mapNotNullTo(mutableSetOf()) { (_, plugin) ->
        plugin.pluginBean.takeIf { it.id in plugins }
    }
)

fun AgentConfig.toAgentInfo() = AgentInfo(
    id = id,
    name = id,
    status = AgentStatus.NOT_REGISTERED,
    serviceGroup = serviceGroupId,
    environment = "",
    description = "",
    agentVersion = agentVersion,
    buildVersion = buildVersion,
    agentType = agentType
)

fun AgentInfo.toDto(agentManager: AgentManager): AgentInfoDto = AgentInfoDto(
    id = id,
    serviceGroup = serviceGroup,
    instanceIds = agentManager.instanceIds(id),
    name = name,
    description = description,
    environment = environment,
    status = status,
    buildVersion = buildVersion,
    adminUrl = adminUrl,
    ipAddress = ipAddress,
    activePluginsCount = plugins.activePluginsCount(),
    agentType = agentType.notation,
    agentVersion = agentVersion,
    systemSettings = agentManager.adminData(id).settings,
    plugins = agentManager.plugins.values.ofAgent(this).mapToDto().toSet()
)

private fun Iterable<AgentInfo>.plugins(): List<PluginMetadata> = run {
    flatMap(AgentInfo::plugins).distinctBy(PluginMetadata::id)
}

private fun Set<PluginMetadata>.activePluginsCount() = this.count { it.enabled }
