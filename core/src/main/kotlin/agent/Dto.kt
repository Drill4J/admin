package com.epam.drill.admin.agent

import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.common.*
import kotlinx.serialization.*

@Serializable
data class SystemSettingsDto(
    val packagesPrefixes: List<String> = emptyList(),
    val sessionIdHeaderName: String = ""
)

@Serializable
data class AgentInfoDto(
    val id: String,
    val serviceGroup: String,
    val instanceIds: Set<String>,
    val name: String,
    val description: String = "",
    val environment: String = "",
    val status: AgentStatus,
    val buildVersion: String,
    val adminUrl: String = "",
    val ipAddress: String = "",
    val activePluginsCount: Int = 0,
    val sessionIdHeaderName: String = "",
    val plugins: Set<PluginDto> = emptySet(),
    val packagesPrefixes: List<String>,
    val agentType: String
)

@Serializable
data class AgentRegistrationDto(
    val name: String,
    val description: String = "",
    val environment: String = "",
    val packagesPrefixes: List<String>,
    val sessionIdHeaderName: String = "",
    val plugins: List<String> = emptyList()
)

@Serializable
data class AgentUpdateDto(
    val name: String,
    val description: String = "",
    val environment: String = ""
)

fun AgentInfo.toDto(agentManager: AgentManager, isList: Boolean = false): AgentInfoDto = agentManager.run {
    AgentInfoDto(
        id = id,
        serviceGroup = serviceGroup,
        instanceIds = instanceIds(id),
        name = name,
        description = if (isList) description.take(200) else description,
        environment = environment,
        status = status,
        buildVersion = buildVersion,
        adminUrl = adminUrl,
        ipAddress = ipAddress,
        activePluginsCount = this@toDto.plugins.activePluginsCount(),
        sessionIdHeaderName = sessionIdHeaderName,
        plugins = this@toDto.plugins.mapToDto().toSet(),
        packagesPrefixes = adminData(id).packagesPrefixes,
        agentType = agentType.notation
    )
}

private fun Set<PluginMetadata>.activePluginsCount() = this.count { it.enabled }
