package com.epam.drill.admin.agent

import kotlinx.serialization.*

@Serializable
data class SystemSettingsDto(
    val packagesPrefixes: List<String> = emptyList(),
    val sessionIdHeaderName: String = ""
)
