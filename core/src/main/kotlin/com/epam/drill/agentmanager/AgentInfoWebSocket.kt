package com.epam.drill.agentmanager

import com.epam.drill.common.*
import com.epam.drill.plugins.*
import kotlinx.serialization.*

@Suppress("unused")
@Serializable
open class AgentInfoWebSocket(
    val id: String,
    val name: String,
    val description: String,
    val group: String? = "",
    val status: AgentStatus = AgentStatus.NOT_REGISTERED,
    var buildVersion: String,
    var buildAlias: String,

    val adminUrl: String = "",
    var ipAddress: String = "",
    val activePluginsCount: Int = 0,
    val pluginsCount: Int = 0
)

@Suppress("unused")
@Serializable
data class AgentInfoWebSocketSingle(
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
    val pluginsCount: Int = 0,
    val sessionIdHeaderName: String = "",
    val rawPluginsName: MutableSet<PluginWebSocket> = mutableSetOf(),
    val buildVersions: MutableSet<AgentBuildVersionJson> = mutableSetOf()
)

fun AgentInfo.toAgentInfoWebSocket() = AgentInfoWebSocketSingle(
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
    pluginsCount = plugins.size,
    sessionIdHeaderName = sessionIdHeaderName,
    rawPluginsName = plugins.toPluginsWebSocket(),
    buildVersions = this.buildVersions
)

fun MutableSet<PluginMetadata>.activePluginsCount() = this.count { it.enabled }

fun MutableSet<AgentInfo>.toAgentInfosWebSocket() = this.map {
    it.run {
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
            pluginsCount = plugins.size
        )
    }
}