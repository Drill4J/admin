package com.epam.drill.admin.servicegroup

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.api.agent.*
import com.epam.kodux.*
import kotlinx.serialization.*

@Serializable
data class ServiceGroup(
    @Id val id: String,
    val name: String,
    val description: String = "",
    val environment: String = "",
    val systemSettings: SystemSettingsDto
)

internal typealias GroupedAgents = Pair<SingleAgents, List<AgentGroup>>

internal sealed class AgentList

internal class SingleAgents(val agentInfos: List<AgentInfo>) : AgentList()

internal class AgentGroup(val group: ServiceGroup, val agentInfos: List<AgentInfo>) : AgentList()
