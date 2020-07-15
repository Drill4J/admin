package com.epam.drill.admin.agent.logging

import com.epam.drill.admin.api.*
import com.epam.kodux.*
import kotlinx.serialization.*

@Serializable
data class AgentLoggingConfig(
    @Id val agentId: String,
    val config: LoggingConfigDto = defaultLoggingConfig
)

val defaultLoggingConfig = LoggingConfigDto(LogLevel.INFO)
