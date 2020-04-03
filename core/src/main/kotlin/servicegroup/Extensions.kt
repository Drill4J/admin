package com.epam.drill.admin.servicegroup

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.plugins.*

fun GroupedAgents.toDto(agentManager: AgentManager) = GroupedAgentsDto(
    single = first.agentInfos.mapToDto(agentManager),
    grouped = second.map { it.toDto(agentManager) }
)

internal fun AgentGroup.toDto(agentManager: AgentManager) = ServiceGroupDto(
    group = group,
    agents = agentInfos.mapToDto(agentManager),
    plugins = agentInfos.plugins().mapToDto()
)

fun Iterable<Any?>.aggregate(): Any? = filterIsInstance<(Any) -> Any>()
    .takeIf { it.any() }
    ?.reduce { acc, aggregator ->
        @Suppress("UNCHECKED_CAST")
        aggregator(acc) as? (Any) -> Any ?: acc
    }
