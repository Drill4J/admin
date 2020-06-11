package com.epam.drill.admin.agent

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
    val agentType: String,
    val agentVersion: String
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
