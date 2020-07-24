package com.epam.drill.admin.servicegroup

import com.epam.drill.admin.agent.*
import com.epam.drill.common.*
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

typealias GroupedAgents = Pair<SingleAgents, List<AgentGroup>>

sealed class AgentList

class SingleAgents(val agentInfos: List<AgentInfo>) : AgentList()

class AgentGroup(val group: ServiceGroup, val agentInfos: List<AgentInfo>) : AgentList()
