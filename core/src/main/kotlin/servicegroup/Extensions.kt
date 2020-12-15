package com.epam.drill.admin.servicegroup

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.api.group.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.plugins.*

internal fun GroupedAgents.toDto(agentManager: AgentManager) = GroupedAgentsDto(
    single = first.agentInfos.mapToDto(agentManager),
    grouped = second.map { it.toDto(agentManager) }
)

internal fun AgentGroup.toDto(
    agentManager: AgentManager
) = JoinedServiceGroupDto(
    group = group,
    agents = agentInfos.mapToDto(agentManager),
    plugins = agentManager.plugins.values.mapToDto(agentInfos)
)

internal fun ServiceGroup.toDto() = ServiceGroupDto(
    id = id,
    name = name,
    description = description,
    environment = environment,
    systemSettings = systemSettings
)

internal fun ServiceGroupDto.toModel() = ServiceGroup(
    id = id,
    name = name,
    description = description,
    environment = environment,
    systemSettings = systemSettings
)
