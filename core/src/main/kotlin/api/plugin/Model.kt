package com.epam.drill.admin.api.plugin

import kotlinx.serialization.Serializable

@Serializable
data class PluginId(val pluginId: String)

@Serializable
data class PluginDto(
    val id: String,
    val type: String = "",
    val version: String = "",
    val name: String = "",
    val description: String = "",
    val status: Boolean = true, //TODO rename to "enabled" or replace with an enum
    val config: String = "",
    val installedAgentsCount: Int = 0, //TODO remove
    val relation: String = "" //TODO remove
)
