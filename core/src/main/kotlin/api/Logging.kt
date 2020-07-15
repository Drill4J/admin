package com.epam.drill.admin.api

import kotlinx.serialization.*

@Serializable
data class LoggingConfigDto(
    val level: LogLevel
)

enum class LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR
}
