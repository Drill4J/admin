package com.epam.drill.plugins.test2code.multibranch.repository

import com.epam.drill.common.agent.configuration.AgentConfig
import com.epam.drill.common.agent.configuration.AgentType
import com.epam.drill.plugins.test2code.common.api.*
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import com.epam.drill.plugins.test2code.multibranch.rawdata.config.DatabaseConfig
import com.sun.tracing.Probe
import io.ktor.util.reflect.*

private const val LONG_TEXT_LENGTH = 65535 // java class name max len
private const val MEDIUM_TEXT_LENGTH = 1000
private const val SHORT_TEXT_LENGTH = 255
//object AgentConfigTable : IntIdTable("test2code.agent_config") {

object AgentConfigTable : IntIdTable("auth.agent_config") {
    val agentId = varchar("agent_id",  SHORT_TEXT_LENGTH)
    val instanceId = varchar("instance_id",  SHORT_TEXT_LENGTH)
    val serviceGroupId = varchar("service_group_id",  SHORT_TEXT_LENGTH)
    val buildVersion = varchar("build_version",  SHORT_TEXT_LENGTH)
    val agentType = varchar("agent_type",  SHORT_TEXT_LENGTH)
    val agentVersion = varchar("agent_version",  SHORT_TEXT_LENGTH).nullable()
}

//object AstMethodTable : IntIdTable("test2code.ast_method") {

object AstMethodTable : IntIdTable("auth.ast_method") {
    val instanceId = varchar("instance_id", SHORT_TEXT_LENGTH) // use reference
    val className = varchar("class_name",  LONG_TEXT_LENGTH)
    val name = varchar("name",  LONG_TEXT_LENGTH)
    val params = varchar("params",  LONG_TEXT_LENGTH) // logically, it could be longer
    val returnType = varchar("return_type",  LONG_TEXT_LENGTH)
    val probesCount = integer("probes_count")
}

data class AstEntityData(
    val instanceId: String,
    val className: String,
    val name: String,
    val params: String,
    val returnType: String,
    val probesCount: Int
)

//object ExecClassDataTable : IntIdTable("test2code.exec_class_data") {
object ExecClassDataTable : IntIdTable("auth.exec_class_data") {
    val instanceId = varchar("instance_id", SHORT_TEXT_LENGTH) // use reference
    val className = varchar("class_name",  LONG_TEXT_LENGTH)
    val testId = varchar("test_id",  SHORT_TEXT_LENGTH)
    val probes = myAwesomeColumn("probes")
}

data class RawCoverageData(
    val instanceId: String,
    val className: String,
    val testId: String,
    val probes: Probes
)

class MyAwesomeColumnType(nullable: Boolean = false) : ColumnType(nullable) {
    override fun sqlType(): String = "VARBIT"
}

fun Table.myAwesomeColumn(name: String, nullable: Boolean = false): Column<ByteArray> =
    registerColumn(name, MyAwesomeColumnType(nullable))


object RawDataRepositoryImpl : RawDataRepositoryWriter, RawDataRepositoryReader {

    // RawDataRepositoryWriter
    override suspend fun saveAgentConfig(agentConfig: AgentConfig) {
        DatabaseConfig.transaction {
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

    override suspend fun saveInitDataPart(instId: String, initDataPart: InitDataPart) {
        DatabaseConfig.transaction {
            initDataPart.astEntities.flatMap { astEntity ->
                astEntity.methods.map { astMethod ->
                    AstEntityData(
                        instanceId = instId,
                        className = "${astEntity.path}/${astEntity.name}",
                        name = astMethod.name,
                        params = astMethod.params.joinToString(","),
                        returnType = astMethod.returnType,
                        probesCount = astMethod.count
                    )
                }
            }.let { dataToInsert ->
                AstMethodTable.batchInsert(dataToInsert) {
                    this[AstMethodTable.instanceId] = it.instanceId
                    this[AstMethodTable.className] = it.className
                    this[AstMethodTable.name] = it.name
                    this[AstMethodTable.params] = it.params
                    this[AstMethodTable.returnType] = it.returnType
                    this[AstMethodTable.probesCount] = it.probesCount
                }
            }
        }
    }

    override suspend fun saveCoverDataPart(instId: String, coverDataPart: CoverDataPart) {
        val batchSize = 100
        java.sql.Types.BIT
        val sql = "INSERT INTO auth.exec_class_data (instance_id, class_name, test_id, probes) VALUES (?, ?, ?, CAST(? AS VARBIT))"
        val dataToInsert = coverDataPart.data
        DatabaseConfig.getDataSource()?.connection.use { connection ->
            if (connection === null) return

            // Disable auto-commit for batch processing
            connection.autoCommit = false

            // Prepare the statement
            val preparedStatement = connection.prepareStatement(sql)

            try {
                var currentBatchSize = 0

                // Iterate through the data and add to the batch
                for ((_, className, probes, _, testId) in dataToInsert) {
                    preparedStatement.setString(1, instId)
                    preparedStatement.setString(2, className)
                    preparedStatement.setString(3, testId)
                    preparedStatement.setString(4, probes.toBitString()) // Assuming probes is a BitSet or a suitable type

                    preparedStatement.addBatch()
                    currentBatchSize++

                    // Execute batch periodically
                    if (currentBatchSize % batchSize == 0) {
                        preparedStatement.executeBatch()
                        currentBatchSize = 0
                    }
                }

                // Execute the remaining batch
                preparedStatement.executeBatch()

                // Commit the transaction
                connection.commit()
            } catch (e: Exception) {
                // Rollback the transaction in case of an exception
                connection.rollback()
                throw e
            } finally {
                // Close the resources
                preparedStatement.close()
            }
        }
    }

    // TODO beware - this is not optimized at all
    fun Probes.toBitString(): String = (0 until length()).map { if (get(it)) '1' else '0' }.joinToString("")

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
    override suspend fun getAgentConfigs(agentId: String, buildVersion: String): List<AgentConfig> {
        return DatabaseConfig.transaction {
            AgentConfigTable
                .select { (AgentConfigTable.agentId eq agentId) and (AgentConfigTable.buildVersion eq buildVersion) }
                .map { it.toAgentConfig() }
        }
    }

    override suspend fun getAstEntities(agentId: String, buildVersion: String): List<AstEntityData> {
        return DatabaseConfig.transaction {
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
        return DatabaseConfig.transaction {
            val instanceIds = getAgentConfigs(agentId, buildVersion).map { it.instanceId }
            ExecClassDataTable
                .select { ExecClassDataTable.instanceId inList instanceIds }
                .map { it.toRawCoverageData() }
        }
    }
}

private fun ResultRow.toAgentConfig() = AgentConfig(
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
    probesCount = this[AstMethodTable.probesCount]
)

// TODO classId and sessionId are omitted. Decide if they are required
private fun ResultRow.toRawCoverageData() = RawCoverageData(
    instanceId = this[ExecClassDataTable.instanceId],
    className = this[ExecClassDataTable.className],
    testId = this[ExecClassDataTable.testId],

    // TODO make sure it works "as intended" and preserves all probes in order
    //      as they were inserted
    probes = Probes.valueOf(this[ExecClassDataTable.probes]),
)
