package com.epam.drill.admin.agent

import com.epam.drill.api.dto.*
import com.epam.kodux.*
import kotlinx.serialization.*

@Serializable
data class AgentLoggingConfig(
    @Id val agentId: String,
    var config: LoggingConfig = defaultLoggingConfig
)

val defaultLoggingConfig = LoggingConfig(
    warn = true,
    info = true,
    debug = false,
    trace = false,
    error = true
)

fun LoggingConfig.associateWithAgent(agentId: String) = AgentLoggingConfig(
    agentId = agentId,
    config = this
)
