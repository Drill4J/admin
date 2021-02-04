package com.epam.drill.admin.agent

import com.epam.drill.admin.api.agent.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.admin.plugins.*

internal fun Iterable<AgentInfo>.mapToDto(
    agentManager: AgentManager
): List<AgentInfoDto> = map { it.toDto(agentManager) }

internal fun AgentManager.all(): List<AgentInfoDto> = agentStorage.values.map { entry ->
    entry.agent.toDto(this)
}

internal fun Plugins.ofAgent(info: AgentInfo) = info.plugins.mapNotNull { this[it] }

internal fun AgentCreationDto.toAgentInfo(installedPlugins: Plugins) = AgentInfo(
    id = id,
    agentType = agentType,
    name = name,
    status = AgentStatus.OFFLINE,
    serviceGroup = serviceGroup,
    environment = environment,
    description = description,
    agentVersion = "",
    buildVersion = "",
    plugins = plugins.filterTo(mutableSetOf()) { it in installedPlugins }
)

internal fun CommonAgentConfig.toAgentInfo() = AgentInfo(
    id = id,
    name = id,
    status = AgentStatus.NOT_REGISTERED,
    serviceGroup = serviceGroupId,
    environment = "",
    description = "",
    agentVersion = agentVersion,
    buildVersion = buildVersion,
    agentType = AgentType.valueOf(agentType.name)
)

internal fun AgentInfo.toDto(
    agentManager: AgentManager
): AgentInfoDto = run {
    val plugins = agentManager.plugins.ofAgent(this)
    val instanceIds = agentManager.instanceIds(id, buildVersion).keys
    AgentInfoDto(
        id = id,
        serviceGroup = serviceGroup,
        instanceIds = instanceIds,
        name = name,
        description = description,
        environment = environment,
        status = status.takeIf { instanceIds.any() } ?: AgentStatus.OFFLINE,
        buildVersion = buildVersion,
        adminUrl = adminUrl,
        ipAddress = ipAddress,
        activePluginsCount = plugins.activePluginsCount(),
        agentType = agentType.notation,
        agentVersion = agentVersion,
        systemSettings = agentManager.adminData(id).settings,
        plugins = plugins.mapToDto().toSet()
    )
}

internal fun AgentInfo.toCommonInfo() = CommonAgentInfo(
    id = id,
    agentType = agentType.name,
    agentVersion = agentVersion,
    serviceGroup = serviceGroup,
    buildVersion = buildVersion,
    name = name,
    environment = environment,
    description = description
)

internal suspend fun Iterable<AgentWsSession>.applyEach(block: suspend AgentWsSession.() -> Unit) = forEach {
    block(it)
}

private fun Iterable<Plugin>.activePluginsCount(): Int = count { it.pluginBean.enabled }
