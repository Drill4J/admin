package com.epam.drill.admin.plugins

import com.epam.drill.admin.agent.*
import com.epam.drill.common.*
import kotlinx.serialization.*

@Serializable
data class PluginDto(
    var id: String,
    var name: String = "",
    var description: String = "",
    var type: String = "",
    var status: Boolean? = true,
    var config: String? = "",
    var installedAgentsCount: Int? = 0,
    var relation: String?
)

fun PluginMetadata.toDto() = PluginDto(
    id = id,
    name = name,
    description = description,
    type = type,
    status = enabled,
    config = config,
    installedAgentsCount = 0,
    relation = null
)

fun Iterable<PluginMetadata>.mapToDto() = map(PluginMetadata::toDto)

fun Iterable<PluginMetadata>.mapToDto(agents: Iterable<AgentInfo>) = map { pb ->
    return@map pb.toDto().apply {
        config = null
        status = null
        installedAgentsCount = agents.byPluginId(id).count()
    }
}
