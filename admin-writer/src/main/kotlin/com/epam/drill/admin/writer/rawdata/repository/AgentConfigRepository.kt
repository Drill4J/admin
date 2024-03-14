package com.epam.drill.admin.writer.rawdata.repository

import com.epam.drill.admin.writer.rawdata.table.AgentConfigTable
import com.epam.drill.common.agent.configuration.AgentMetadata
import org.jetbrains.exposed.sql.insertAndGetId

object AgentConfigRepository {

    fun create(agentConfig: AgentMetadata): Int {
        return AgentConfigTable.insertAndGetId {
            it[agentId] = agentConfig.id
            it[instanceId] = agentConfig.instanceId
            it[buildVersion] = agentConfig.buildVersion
            it[serviceGroupId] = agentConfig.serviceGroupId
            it[agentType] = agentConfig.agentType.notation
            it[agentVersion] = agentConfig.agentVersion
        }.value
    }
}