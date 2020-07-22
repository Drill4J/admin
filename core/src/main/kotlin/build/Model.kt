package com.epam.drill.admin.build

import com.epam.drill.common.*
import com.epam.kodux.*
import kotlinx.serialization.*

@Serializable
data class AgentBuild(
    @Id val id: AgentBuildId,
    val agentId: String,
    val info: BuildInfo,
    val detectedAt: Long = 0L
)

@Serializable
data class AgentBuildId(
    val agentId: String,
    val version: String
) : Comparable<AgentBuildId> {
    override fun compareTo(other: AgentBuildId): Int = agentId.compareTo(other.agentId).takeIf { it != 0 }
        ?: version.compareTo(other.version)
}

@Serializable
data class AgentBuildData(
    @Id val id: AgentBuildId,
    val agentId: String,
    val parentVersion: String,
    val detectedAt: Long,
    val codeData: ByteArray
) {
    override fun equals(other: Any?) = other is AgentBuildData && id == other.id

    override fun hashCode() = id.hashCode()
}

@Serializable
data class CodeData(val classBytes: Map<String, ByteArray> = emptyMap())
