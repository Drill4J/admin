package com.epam.drill.admin.api.plugin

import kotlinx.serialization.*

@Serializable
data class PluginId(val pluginId: String)

@Serializable
data class PluginDto(
    val id: String,
    val version: String = "",
    val name: String = "",
    val description: String = "",
    val relation: String = "", //TODO remove
    val available: Boolean = false
)
