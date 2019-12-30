package com.epam.drill.agentmanager

import com.epam.drill.common.*
import com.epam.drill.endpoints.*
import com.epam.drill.plugins.*
import kotlinx.serialization.*

//TODO Rename to AgentInfoDto
@Serializable
data class AgentInfoWebSocket(
    val id: String,
    val instanceIds: Set<String>,
    val name: String,
    val description: String,
    val environment: String = "",
    val status: AgentStatus,
    val buildVersion: String,
    val buildAlias: String,
    val adminUrl: String = "",
    val ipAddress: String = "",
    val activePluginsCount: Int = 0,
    val sessionIdHeaderName: String = "",
    val plugins: Set<PluginWebSocket> = emptySet(),
    val packagesPrefixes: List<String>,
    val agentType: String
)

fun Set<PluginMetadata>.activePluginsCount() = this.count { it.enabled }

fun MutableSet<AgentInfo>.toAgentInfosWebSocket(agentManager: AgentManager) = map { agentInfo ->
    agentInfo.toDto(agentManager, isList = true)
}

fun AgentInfo.toDto(agentManager: AgentManager, isList: Boolean = false): AgentInfoWebSocket = agentManager.run {
    AgentInfoWebSocket(
        id = id,
        instanceIds = instanceIds,
        name = name,
        description = if (isList) description.take(200) else description,
        environment = environment,
        status = status,
        buildVersion = buildVersion,
        buildAlias = buildAlias,
        adminUrl = adminUrl,
        ipAddress = ipAddress,
        activePluginsCount = this@toDto.plugins.activePluginsCount(),
        sessionIdHeaderName = sessionIdHeaderName,
        plugins = this@toDto.plugins.toPluginsWebSocket(),
        packagesPrefixes = adminData(id).packagesPrefixes,
        agentType = agentType.notation
    )
}

fun AgentInfo.toAgentInfoWebSocket(agentManager: AgentManager) = toDto(agentManager)
