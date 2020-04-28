package com.epam.drill.admin.admindata

import com.epam.drill.admin.build.*
import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import com.epam.kodux.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.*
import mu.*

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
            agentId = agentId,
            version = info.version,
            parentVersion = info.parentVersion,
            detectedAt = detectedAt,
            methodChanges = info.methodChanges,
            classes = info.classesBytes.map { (name, bytes) ->
                AgentBuildClass(
                    name = name,
                    methods = agentBuild.info.javaMethods[name] ?: emptyList(),
                    bytes = bytes
                )
            }
        )
        storeClient.executeInAsyncTransaction {
            store(toSummary())
            store(buildData)
        }
        logger.debug { "Saved build ${agentBuild.id}." }
    }

    suspend fun loadStoredData() = storeClient.findById<AdminDataSummary>(agentId)?.let { summary ->
        logger.debug { "Loading data for $agentId..." }
        packagesPrefixes = summary.packagesPrefixes
        val builds: List<AgentBuild> = storeClient.findBy<AgentBuildData> {
            AgentBuildData::agentId eq agentId
        }.map { data ->
            data.run {
                AgentBuild(
                    id = id,
                    agentId = agentId,
                    detectedAt = detectedAt,
                    info = BuildInfo(
                        version = version,
                        parentVersion = parentVersion,
                        methodChanges = methodChanges,
                        javaMethods = classes.associate { it.name to it.methods },
                        classesBytes = classes.associate { it.name to it.bytes }
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
