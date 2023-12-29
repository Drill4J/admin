package com.epam.drill.plugins.test2code.multibranch.repository

import com.epam.drill.common.agent.configuration.AgentConfig
import com.epam.drill.plugins.test2code.common.api.CoverDataPart
import com.epam.drill.plugins.test2code.common.api.ExecClassData
import com.epam.drill.plugins.test2code.common.api.InitDataPart

interface RawDataRepositoryWriter {
    suspend fun saveAgentConfig(agentConfig: AgentConfig)
    suspend fun saveInitDataPart(instanceId: String, initDataPart: InitDataPart)
    suspend fun saveCoverDataPart(instanceId: String, coverDataPart: CoverDataPart)

}
