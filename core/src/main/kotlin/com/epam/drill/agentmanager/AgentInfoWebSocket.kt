package com.epam.drill.agentmanager

import com.epam.drill.admindata.*
import com.epam.drill.common.*
import com.epam.drill.plugins.*
import kotlinx.serialization.*

@Serializable
data class AgentInfoWebSocket(
    val id: String,
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
    val packagesPrefixes: List<String>
)

fun MutableSet<PluginMetadata>.activePluginsCount() = this.count { it.enabled }

fun MutableSet<AgentInfo>.toAgentInfosWebSocket(adminDataVault: AdminDataVault) = this.map { agentInfo ->
    agentInfo.run {
        AgentInfoWebSocket(
            id = id,
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
            plugins = plugins.map { it.toPluginWebSocket() }.toMutableSet(),
            packagesPrefixes = adminDataVault[id]?.packagesPrefixes ?: emptyList()
        )
    }
}

fun AgentInfo.toAgentInfoWebSocket(adminData: AdminPluginData) = AgentInfoWebSocket(
    id = id,
    name = name,
    description = description,
    group = groupName,
    status = status,
    buildVersion = buildVersion,
    buildAlias = buildAlias,
    adminUrl = adminUrl,
    ipAddress = ipAddress,
    activePluginsCount = plugins.activePluginsCount(),
    sessionIdHeaderName = sessionIdHeaderName,
    plugins = plugins.toPluginsWebSocket(),
    packagesPrefixes = adminData.packagesPrefixes
)