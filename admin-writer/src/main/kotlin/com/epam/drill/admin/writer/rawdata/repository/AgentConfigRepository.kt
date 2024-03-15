/**
 * Copyright 2020 - 2022 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.drill.admin.writer.rawdata.repository

import com.epam.drill.admin.writer.rawdata.table.AgentConfigTable
import com.epam.drill.common.agent.configuration.AgentMetadata
import com.epam.drill.common.agent.configuration.AgentType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select

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

    fun findAllByAgentIdAndBuildVersion(agentId: String, buildVersion: String): List<AgentMetadata> {
        return AgentConfigTable
            .select { (AgentConfigTable.agentId eq agentId) and (AgentConfigTable.buildVersion eq buildVersion) }
            .map { it.toAgentConfig() }
    }

    private fun ResultRow.toAgentConfig() = AgentMetadata(
        id = this[AgentConfigTable.agentId],
        serviceGroupId = this[AgentConfigTable.serviceGroupId],
        instanceId = this[AgentConfigTable.instanceId],
        agentType = try {
            AgentType.values().find { it.notation.equals(this[AgentConfigTable.agentType], ignoreCase = true) }
                ?: AgentType.DOTNET
//            AgentType.valueOf(this[AgentConfigTable.agentType])
        } catch (e: IllegalArgumentException) {
            println("123 unknown agent type ${this[AgentConfigTable.agentType]}")
            // Handle the case when the enum constant doesn't exist
            // You might want to log the error or provide a default value
            AgentType.DOTNET
        },
        buildVersion = this[AgentConfigTable.buildVersion],
    )
}