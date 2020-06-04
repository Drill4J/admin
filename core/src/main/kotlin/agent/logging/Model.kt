package com.epam.drill.admin.agent.logging

import com.epam.drill.api.dto.*
import com.epam.kodux.*
import kotlinx.serialization.*

@Serializable
data class AgentLoggingConfig(
    @Id val agentId: String,
    val config: LoggingConfig = defaultLoggingConfig
)

val defaultLoggingConfig = LoggingConfig(
    info = true,
    warn = true,
    error = true
)
