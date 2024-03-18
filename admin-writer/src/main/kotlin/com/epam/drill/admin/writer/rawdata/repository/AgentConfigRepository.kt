package com.epam.drill.admin.writer.rawdata.repository

import com.epam.drill.common.agent.configuration.AgentMetadata

interface AgentConfigRepository {
    fun create(agentConfig: AgentMetadata): Int
    fun findAllByAgentIdAndBuildVersion(agentId: String, buildVersion: String): List<AgentMetadata>
}