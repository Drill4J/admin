package com.epam.drill.admin.servicegroup

import com.epam.drill.admin.admindata.*
import com.epam.drill.admin.agent.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.plugins.*

fun GroupedAgents.toDto(agentManager: AgentManager) = GroupedAgentsDto(
    single = first.agentInfos.mapToDto(agentManager),
    grouped = second.map { it.toDto(agentManager) }
)

internal fun AgentGroup.toDto(agentManager: AgentManager) = ServiceGroupDto(
    group = group,
    agents = agentInfos.mapToDto(agentManager),
    plugins = agentInfos.plugins().mapToDto()
)

internal fun AgentEntry.toPluginSummaryDto(adminData: AdminPluginData, data: Any) = PluginSummaryDto(
    agentId = agent.id,
    agentName = agent.name,
    lastBuild = adminData.buildManager.run {
        LastBuildDto(
            version = lastBuild,
            alias = buildVersions[lastBuild] ?: ""
        )
    },
    data = data
)