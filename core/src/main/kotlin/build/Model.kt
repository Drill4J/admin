package com.epam.drill.admin.build

import com.epam.drill.common.*
import com.epam.kodux.*
import kotlinx.serialization.*

@Serializable
data class AgentBuild(
    @Id val id: String,
    val agentId: String,
    val info: BuildInfo,
    val detectedAt: Long = 0L
)
