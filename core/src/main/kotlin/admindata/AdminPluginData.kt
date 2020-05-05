package com.epam.drill.admin.admindata

import com.epam.drill.admin.build.*
import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import com.epam.kodux.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.*
import mu.*
import kotlin.time.*

private val logger = KotlinLogging.logger {}

class AdminDataVault {
    private val _data = atomic(persistentMapOf<String, AdminPluginData>())

    operator fun get(key: String): AdminPluginData? = _data.value[key]

    fun getOrPut(
        key: String,
        provider: () -> AdminPluginData
    ): AdminPluginData = get(key) ?: provider().let { data ->
        _data.updateAndGet {
            if (key !in it) {
                it.put(key, data)
            } else it
        }[key]!!
    }
}

class AdminPluginData(
    val agentId: String,
    private val storeClient: StoreClient,
    defaultPackages: List<String>
) : AdminData {

    override val buildManager get() = _buildManager.value

    var packagesPrefixes: List<String>
        get() = _packagesPrefixes.value
        set(value) {
            _packagesPrefixes.value = value
        }

    private val _buildManager = atomic(AgentBuildManager(agentId))

    private val _packagesPrefixes = atomic(defaultPackages)

    suspend fun store(agentBuild: AgentBuild) = agentBuild.run {
        logger.debug { "Saving build ${agentBuild.id}..." }
        val buildData = AgentBuildData(
            id = id,
            agentId = id.agentId,
            parentVersion = info.parentVersion,
            detectedAt = detectedAt,
            codeData = ProtoBuf.dump(
                CodeData.serializer(), CodeData(
                    classBytes = info.classesBytes,
                    methods = info.javaMethods,
                    methodChanges = info.methodChanges.map.map {
                        DiffTypeMethods(
                            type = it.key,
                            methods = it.value
                        )
                    }
                )
            )
        )
        measureTime {
            storeClient.executeInAsyncTransaction {
                store(toSummary())
                store(buildData)
            }
        }.let { duration -> logger.debug { "Saved build ${agentBuild.id} in $duration." } }

        logger.debug { "Saved build ${agentBuild.id}." }
    }

    suspend fun loadStoredData() = storeClient.findById<AdminDataSummary>(agentId)?.let { summary ->
        logger.debug { "Loading data for $agentId..." }
        packagesPrefixes = summary.packagesPrefixes
        val builds: List<AgentBuild> = storeClient.findBy<AgentBuildData> {
            AgentBuildData::agentId eq agentId
        }.map { data ->
            data.run {
                val codeData = ProtoBuf.load(CodeData.serializer(), codeData)
                AgentBuild(
                    id = id,
                    agentId = agentId,
                    detectedAt = detectedAt,
                    info = BuildInfo(
                        version = id.version,
                        parentVersion = parentVersion,
                        methodChanges = MethodChanges(codeData.methodChanges.associate { it.type to it.methods }),
                        javaMethods = codeData.methods,
                        classesBytes = codeData.classBytes
                    )
                )
            }
        }
        _buildManager.value = AgentBuildManager(
            agentId = agentId,
            builds = builds,
            lastBuild = summary.lastBuild
        )
        logger.debug { "Loaded data for $agentId" }
    }

    suspend fun resetBuilds() {
        _buildManager.value = AgentBuildManager(agentId)
        storeClient.executeInAsyncTransaction {
            deleteBy<AgentBuild> { AgentBuild::agentId eq agentId }
            store(toSummary())
        }
    }

    private fun toSummary(): AdminDataSummary {
        return AdminDataSummary(
            agentId = agentId,
            packagesPrefixes = packagesPrefixes,
            lastBuild = buildManager.lastBuild
        )
    }
}

@Serializable
data class AdminDataSummary(
    @Id val agentId: String,
    val packagesPrefixes: List<String>,
    val lastBuild: String
)
