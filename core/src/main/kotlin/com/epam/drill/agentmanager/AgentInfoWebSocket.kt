package com.epam.drill.agentmanager

import com.epam.drill.common.*
import com.epam.drill.endpoints.*
import com.epam.drill.plugins.*
import kotlinx.serialization.*

@Serializable
data class AgentInfoWebSocket(
    val id: String,
    val instanceIds: Set<String>,
    val name: String,
    val description: String,
    val group: String = "",
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

fun MutableSet<AgentInfo>.toAgentInfosWebSocket(agentManager: AgentManager) = this.map { agentInfo ->
    agentManager.run {
        agentInfo.run {
            AgentInfoWebSocket(
                id = id,
                instanceIds = instanceIds,
                name = name,
                description = description.take(200),
                group = groupName,
                status = status,
                buildVersion = buildVersion,
                buildAlias = buildAlias,
                adminUrl = adminUrl,
                ipAddress = ipAddress,
                activePluginsCount = plugins.activePluginsCount(),
                sessionIdHeaderName = sessionIdHeaderName,
                plugins = plugins.map { it.toPluginWebSocket() }.toSet(),
                packagesPrefixes = adminDataVault[id]?.packagesPrefixes ?: emptyList(),
                agentType = agentType.notation
            )
        }
    }
}

fun AgentInfo.toAgentInfoWebSocket(agentManager: AgentManager) = agentManager.run {
    AgentInfoWebSocket(
        id = id,
        instanceIds = instanceIds,
        name = name,
        description = description,
        group = groupName,
        status = status,
        buildVersion = buildVersion,
        buildAlias = buildAlias,
        adminUrl = adminUrl,
        ipAddress = ipAddress,
        activePluginsCount = this@toAgentInfoWebSocket.plugins.activePluginsCount(),
        sessionIdHeaderName = sessionIdHeaderName,
        plugins = this@toAgentInfoWebSocket.plugins.toPluginsWebSocket(),
        packagesPrefixes = adminData(id).packagesPrefixes,
        agentType = agentType.notation
    )
}