package com.epam.drill.admin.api.plugin

import kotlinx.serialization.Serializable

@Serializable
data class PluginId(val pluginId: String)

@Serializable
data class PluginDto(
    var id: String,
    var name: String = "",
    var description: String = "",
    var type: String = "",
    var status: Boolean? = true,
    var config: String? = "",
    var installedAgentsCount: Int? = 0,
    val relation: String?,
    val version: String = ""
)
