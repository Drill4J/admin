package com.epam.drill.plugins.test2code.multibranch.repository

import com.epam.drill.common.agent.configuration.AgentMetadata
import com.epam.drill.plugins.test2code.api.AddTestsPayload
import com.epam.drill.plugins.test2code.common.transport.ClassMetadata
import com.epam.drill.plugins.test2code.common.transport.CoverageData

interface RawDataRepositoryWriter {
    suspend fun saveAgentConfig(agentConfig: AgentMetadata)
    suspend fun saveInitDataPart(instanceId: String, initDataPart: ClassMetadata)
    suspend fun saveCoverDataPart(instanceId: String, coverDataPart: CoverageData)
    suspend fun saveTestMetadata(addTestsPayload: AddTestsPayload)

}
