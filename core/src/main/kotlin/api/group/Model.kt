package com.epam.drill.admin.api.group

import com.epam.drill.admin.api.agent.*
import com.epam.drill.admin.api.plugin.*
import kotlinx.serialization.*

//TODO remove after frontend has moved to topics: api/agents, api/groups
@Serializable
data class JoinedServiceGroupDto(
    val group: ServiceGroupDto,
    val agents: List<AgentInfoDto>,
    val plugins: List<PluginDto>
)

//TODO remove after frontend has moved to topics: api/agents, api/groups
@Serializable
data class GroupedAgentsDto(
    val single: List<AgentInfoDto>,
    val grouped: List<JoinedServiceGroupDto>
)

@Serializable
data class ServiceGroupDto(
    val id: String,
    val name: String,
    val description: String = "",
    val environment: String = "",
    val systemSettings: SystemSettingsDto
)

@Serializable
data class ServiceGroupUpdateDto(
    val name: String,
    val description: String = "",
    val environment: String = ""
)
