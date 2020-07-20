package com.epam.drill.admin.agent

import com.epam.drill.admin.plugins.*
import com.epam.drill.common.*
import kotlinx.serialization.*

@Serializable
data class SystemSettingsDto(
    val packages: List<String> = emptyList(),
    val sessionIdHeaderName: String = "",
    val targetHost: String = ""
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
    val agentType: String,
    val agentVersion: String,
    val packages: List<String> = emptyList(),//TODO remove after integration of system settings
    val sessionIdHeaderName: String = "",//TODO remove after integration of system settings
    val systemSettings: SystemSettingsDto = SystemSettingsDto(),
    val plugins: Set<PluginDto> = emptySet()
)

@Serializable
data class AgentRegistrationDto(
    val name: String,
    val description: String = "",
    val environment: String = "",
    val packages: List<String> = emptyList(),//TODO remove after integration of system settings
    val sessionIdHeaderName: String = "",//TODO remove after integration of system settings
    val systemSettings: SystemSettingsDto = SystemSettingsDto(),
    val plugins: List<String> = emptyList()
)

@Serializable
data class AgentUpdateDto(
    val name: String,
    val description: String = "",
    val environment: String = ""
)
