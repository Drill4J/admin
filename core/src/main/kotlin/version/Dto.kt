package com.epam.drill.admin.version

import kotlinx.serialization.*

@Serializable
data class VersionDto(
    val admin: String,
    val java: String = "",
    val plugins: List<ComponentVersion> = emptyList(),
    val agents: List<ComponentVersion> = emptyList()
)

@Serializable
data class ComponentVersion(
    val id: String,
    val version: String
)
