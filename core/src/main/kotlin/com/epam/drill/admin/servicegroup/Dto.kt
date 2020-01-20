package com.epam.drill.admin.servicegroup

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.admindata.*
import com.epam.drill.common.*
import com.epam.drill.admin.endpoints.*
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
data class LastBuildDto(
    val version: String,
    val alias: String
)

@Serializable
data class PluginSummaryDto(
    val agentId: String,
    val agentName: String,
    val lastBuild: LastBuildDto,
    @ContextualSerialization val data: Any
)

@Serializable
data class ServiceGroupSummaryDto(
    val name: String,
    val summaries: List<PluginSummaryDto>,
    val count: Int,
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

private fun List<AgentInfo>.toDto(agentManager: AgentManager) = map { it.toDto(agentManager) }
