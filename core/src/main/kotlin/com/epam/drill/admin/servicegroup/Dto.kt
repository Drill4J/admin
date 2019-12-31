package com.epam.drill.admin.servicegroup

import com.epam.drill.admindata.*
import com.epam.drill.agentmanager.*
import com.epam.drill.common.*
import com.epam.drill.endpoints.*
import kotlinx.serialization.*

@Serializable
data class ServiceGroupDto(
    val group: ServiceGroup,
    val agents: List<AgentInfoWebSocket>
)

@Serializable
data class GroupedAgentsDto(
    val single: List<AgentInfoWebSocket>,
    val grouped: List<ServiceGroupDto>
)

@Serializable
data class PluginSummaryDto(
    val agentName: String,
    val lastBuild: String,
    @ContextualSerialization val data: Any
)

@Serializable
data class ServiceGroupSummaryDto(
    val name: String,
    val summaries: List<PluginSummaryDto>,
    @ContextualSerialization val aggregatedData: Any
)

fun GroupedAgents.toDto(agentManager: AgentManager) = GroupedAgentsDto(
    single = first.agentInfos.toDto(agentManager),
    grouped = second.map { agentGroup ->
        ServiceGroupDto(
            group = agentGroup.group,
            agents = agentGroup.agentInfos.toDto(agentManager)
        )
    }
)

internal fun AgentEntry.toPluginSummaryDto(adminData: AdminPluginData, data: Any) = PluginSummaryDto(
    agentName = agent.name,
    lastBuild = adminData.buildManager.lastBuild,
    data = data
)

private fun List<AgentInfo>.toDto(agentManager: AgentManager) = map { it.toDto(agentManager) }
