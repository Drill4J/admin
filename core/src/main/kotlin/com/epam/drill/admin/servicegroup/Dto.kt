package com.epam.drill.admin.servicegroup

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

fun GroupedAgents.toDto(agentManager: AgentManager) = GroupedAgentsDto(
    single = first.agentInfos.toDto(agentManager),
    grouped = second.map { agentGroup ->
        ServiceGroupDto(
            group = agentGroup.group,
            agents = agentGroup.agentInfos.toDto(agentManager)
        )
    }
)

private fun List<AgentInfo>.toDto(agentManager: AgentManager) = map { it.toDto(agentManager) }
