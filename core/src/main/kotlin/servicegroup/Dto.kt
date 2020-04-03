package com.epam.drill.admin.servicegroup

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.plugins.*
import kotlinx.serialization.*

@Serializable
data class ServiceGroupDto(
    val group: ServiceGroup,
    val agents: List<AgentInfoDto>,
    val plugins: List<PluginDto>
)

@Serializable
data class GroupedAgentsDto(
    val single: List<AgentInfoDto>,
    val grouped: List<ServiceGroupDto>
)
