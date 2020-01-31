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
