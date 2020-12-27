package com.epam.drill.admin.agent

import com.epam.drill.admin.api.agent.*
import com.epam.kodux.*
import kotlinx.serialization.*

typealias CommonAgentConfig = com.epam.drill.common.AgentConfig
typealias CommonAgentInfo = com.epam.drill.common.AgentInfo

@Serializable
data class AgentInfo(
    @Id val id: String,
    val name: String,
    val status: AgentStatus,
    val serviceGroup: String = "",
    val environment: String = "",
    val description: String,
    val buildVersion: String,
    val agentType: AgentType,
    val agentVersion: String = "",
    val adminUrl: String = "",
    val ipAddress: String = "",
    val plugins: Set<String> = emptySet()
) {
    override fun equals(other: Any?): Boolean = other is AgentInfo && id == other.id

    override fun hashCode(): Int = id.hashCode()
}

@Serializable
internal class PreparedAgentData(
    @Id val id: String,
    val dto: AgentCreationDto
)

@Serializable
internal data class AgentDataSummary(
    @Id val agentId: String,
    val settings: SystemSettingsDto
)

@Serializable
internal class CodeData(val classBytes: Map<String, ByteArray> = emptyMap())

@Serializable
internal class StoredCodeData(
    @Id val id: String,
    val data: ByteArray
)
