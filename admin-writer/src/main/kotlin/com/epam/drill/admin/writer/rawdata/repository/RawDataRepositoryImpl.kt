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

import com.epam.drill.admin.writer.rawdata.config.RawDataWriterDatabaseConfig.transaction
import com.epam.drill.admin.writer.rawdata.entity.AstEntityData
import com.epam.drill.admin.writer.rawdata.entity.RawCoverageData
import com.epam.drill.admin.writer.rawdata.entity.TestMetadata
import com.epam.drill.admin.writer.rawdata.table.AgentConfigTable
import com.epam.drill.admin.writer.rawdata.table.AstMethodTable
import com.epam.drill.admin.writer.rawdata.table.ExecClassDataTable
import com.epam.drill.common.agent.configuration.AgentMetadata
import com.epam.drill.common.agent.configuration.AgentType
import com.epam.drill.plugins.test2code.api.AddTestsPayload
import com.epam.drill.plugins.test2code.common.transport.ClassMetadata
import com.epam.drill.plugins.test2code.common.transport.CoverageData
import org.jetbrains.exposed.sql.*

private const val EXEC_DATA_BATCH_SIZE = 100

object RawDataRepositoryImpl : RawDataRepositoryWriter, RawDataRepositoryReader {

    override suspend fun saveAgentConfig(agentConfig: AgentMetadata) {
        transaction {
            AgentConfigRepository.create(agentConfig)
        }
    }

    override suspend fun saveInitDataPart(instanceId: String, initDataPart: ClassMetadata) {
        initDataPart.astEntities.flatMap { astEntity ->
            astEntity.methods.map { astMethod ->
                AstEntityData(
                    instanceId = instanceId,
                    className = "${astEntity.path}/${astEntity.name}",
                    name = astMethod.name,
                    params = astMethod.params.joinToString(","),
                    returnType = astMethod.returnType,
                    probesCount = astMethod.count,
                    probesStartPos = astMethod.probesStartPos,
                    bodyChecksum = astMethod.checksum
                )
            }
        }.let { dataToInsert ->
            transaction {
                AstMethodRepository.createMany(dataToInsert)
            }
        }
    }

    override suspend fun saveCoverDataPart(instanceId: String, coverDataPart: CoverageData) {
        coverDataPart.execClassData
            .map { execClassData ->
                RawCoverageData(
                    instanceId = instanceId,
                    className = execClassData.className,
                    testId = execClassData.testId,
                    probes = execClassData.probes
                )
            }
            .chunked(EXEC_DATA_BATCH_SIZE)
            .forEach { data ->
                transaction {
                    ExecClassDataRepository.createMany(data)
                }
            }
    }

    override suspend fun saveTestMetadata(addTestsPayload: AddTestsPayload) {
        // addTestsPayload.sessionId
        addTestsPayload.tests.map { test ->
            TestMetadata(
                testId = test.id,
                name = test.details.testName,
                type = "placeholder"
            )
        }.let { dataToInsert ->
            transaction {
                TestMetadataRepository.createMany(dataToInsert)
            }
        }
    }

    override suspend fun getAgentConfigs(agentId: String, buildVersion: String): List<AgentMetadata> {
        return transaction {
            AgentConfigTable
                .select { (AgentConfigTable.agentId eq agentId) and (AgentConfigTable.buildVersion eq buildVersion) }
                .map { it.toAgentConfig() }
        }
    }

    override suspend fun getAstEntities(agentId: String, buildVersion: String): List<AstEntityData> {
        return transaction {
            val instanceIds = getAgentConfigs(agentId, buildVersion).map { it.instanceId }
            AstMethodTable
                .select { AstMethodTable.instanceId inList instanceIds }
                .distinctBy { row ->
                    Pair(row[AstMethodTable.className], row[AstMethodTable.name])
                }
                .map { it.toAstEntityData() }
        }
    }

    override suspend fun getRawCoverageData(agentId: String, buildVersion: String): List<RawCoverageData> {
        return transaction {
            val instanceIds = getAgentConfigs(agentId, buildVersion).map { it.instanceId }
            ExecClassDataTable
                .select { ExecClassDataTable.instanceId inList instanceIds }
                .map { it.toRawCoverageData() }
        }
    }
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

private fun ResultRow.toAstEntityData() = AstEntityData(
    instanceId = this[AstMethodTable.instanceId],
    className = this[AstMethodTable.className],
    name = this[AstMethodTable.name],
    params = this[AstMethodTable.params],
    returnType = this[AstMethodTable.returnType],
    probesCount = this[AstMethodTable.probesCount],
    probesStartPos = this[AstMethodTable.probesStartPos],
    bodyChecksum = this[AstMethodTable.bodyChecksum]
)

// TODO classId and sessionId are omitted. Decide if they are required
private fun ResultRow.toRawCoverageData() = RawCoverageData(
    instanceId = this[ExecClassDataTable.instanceId],
    className = this[ExecClassDataTable.className],
    testId = this[ExecClassDataTable.testId],

    // TODO this is broken 100% since we remove last bit when writing to DB
    //      probably there is no need to read it back - might as well delete getSomething* methods
    // TODO make sure it works "as intended" and preserves all probes in order
    //      as they were inserted
    probes = this[ExecClassDataTable.probes]
)
