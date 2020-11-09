package com.epam.drill.admin.servicegroup

import com.epam.drill.admin.api.agent.*
import com.epam.drill.admin.api.plugin.*
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

@Serializable
data class ServiceGroupUpdateDto(
    val name: String,
    val description: String = "",
    val environment: String = ""
)
