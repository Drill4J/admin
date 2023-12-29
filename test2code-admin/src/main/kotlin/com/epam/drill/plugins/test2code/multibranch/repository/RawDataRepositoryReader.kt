package com.epam.drill.plugins.test2code.multibranch.repository

import com.epam.drill.common.agent.configuration.AgentConfig

interface RawDataRepositoryReader {
    suspend fun getAgentConfigs(agentId: String, buildVersion: String): List<AgentConfig>
    suspend fun getAstEntities(agentId: String, buildVersion: String): List<AstEntityData>
    suspend fun getRawCoverageData(agentId: String, buildVersion: String): List<RawCoverageData>
}
