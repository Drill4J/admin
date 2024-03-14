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
import com.epam.drill.admin.writer.rawdata.table.TestMetadataTable
import com.epam.drill.common.agent.configuration.AgentMetadata
import com.epam.drill.common.agent.configuration.AgentType
import com.epam.drill.plugins.test2code.api.AddTestsPayload
import com.epam.drill.plugins.test2code.common.transport.ClassMetadata
import com.epam.drill.plugins.test2code.common.transport.CoverageData
import org.jetbrains.exposed.sql.*

private const val EXEC_DATA_BATCH_SIZE = 100

object RawDataRepositoryImpl : RawDataRepositoryWriter, RawDataRepositoryReader {

    // RawDataRepositoryWriter
    override suspend fun saveAgentConfig(agentConfig: AgentMetadata) {
        transaction {
            AgentConfigTable
                .insert {
                    it[agentId] = agentConfig.id
                    it[instanceId] = agentConfig.instanceId
                    it[buildVersion] = agentConfig.buildVersion
                    it[serviceGroupId] = agentConfig.serviceGroupId
                    it[agentType] = agentConfig.agentType.notation
                    it[agentVersion] = agentConfig.agentVersion
                }
        }
    }

    override suspend fun saveInitDataPart(instanceId: String, initDataPart: ClassMetadata) {
        transaction {
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
                AstMethodTable.batchInsert(dataToInsert) {
                    this[AstMethodTable.instanceId] = it.instanceId
                    this[AstMethodTable.className] = it.className
                    this[AstMethodTable.name] = it.name
                    this[AstMethodTable.params] = it.params
                    this[AstMethodTable.returnType] = it.returnType
                    this[AstMethodTable.probesStartPos] = it.probesStartPos
                    this[AstMethodTable.bodyChecksum] = it.bodyChecksum
                    this[AstMethodTable.probesCount] = it.probesCount
                }
            }
        }
    }

    override suspend fun saveCoverDataPart(instanceId: String, coverDataPart: CoverageData) {
        coverDataPart.execClassData.chunked(EXEC_DATA_BATCH_SIZE).forEach { data ->
            transaction {
                ExecClassDataTable.batchInsert(data, shouldReturnGeneratedValues = false) {
                    this[ExecClassDataTable.instanceId] = instanceId
                    this[ExecClassDataTable.className] = it.className
                    this[ExecClassDataTable.testId] = it.testId
                    this[ExecClassDataTable.probes] = it.probes
                }
            }
        }
    }

    override suspend fun saveTestMetadata(addTestsPayload: AddTestsPayload) {
        transaction {
            // addTestsPayload.sessionId
            addTestsPayload.tests.map { test ->
                TestMetadata(
                    testId = test.id,
                    name = test.details.testName,
                    type = "placeholder"
                )
            }.let { dataToInsert ->
                TestMetadataTable.batchInsert(dataToInsert) {
                    this[TestMetadataTable.testId] = it.testId
                    this[TestMetadataTable.name] = it.name
                    this[TestMetadataTable.type] = it.type
                }
            }
        }
    }

//    override suspend fun saveCoverDataPart(instId: String, coverDataPart: CoverDataPart) {
//        DatabaseConfig.transaction {
//            val firstElement = coverDataPart.data.firstOrNull()
//
//            if (firstElement != null) {
//                ExecClassDataTable.insert {
//                    it[instanceId] = instId
//                    it[className] = firstElement.className
//                    it[testId] = firstElement.testId
//                    it[probes] = firstElement.probes.toByteArray()
//                }
//            }
//        }
//    }

//    override suspend fun saveCoverDataPart(instId: String, coverDataPart: CoverDataPart) {
//        DatabaseConfig.transaction {
//            ExecClassDataTable.batchInsert(coverDataPart.data) {
//                this[ExecClassDataTable.instanceId] = instId
//                this[ExecClassDataTable.className] = it.className
//                this[ExecClassDataTable.testId] = it.testId
//                this[ExecClassDataTable.probes] = it.probes.toByteArray()
//            }
//        }
//    }

    // RawDataRepositoryReader
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
              AgentType.values().find { it.notation.equals(this[AgentConfigTable.agentType], ignoreCase = true) } ?: AgentType.DOTNET
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
