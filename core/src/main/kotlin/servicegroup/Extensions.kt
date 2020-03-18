package com.epam.drill.admin.servicegroup

import com.epam.drill.admin.admindata.*
import com.epam.drill.admin.agent.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.plugin.api.end.*

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
    buildVersion = adminData.buildManager.lastBuild,
    data = data
)

fun Iterable<Reducible?>.aggregate(): Reducible? = filterIsInstance<Reducible>()
    .takeIf { it.any() }
    ?.reduce { acc, it -> acc.reduce(it) as Reducible }
