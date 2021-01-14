package com.epam.drill.admin.agent

import com.epam.drill.admin.admindata.*
import com.epam.drill.admin.api.agent.*
import com.epam.drill.admin.build.*
import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.store.*
import com.epam.drill.admin.util.*
import com.epam.drill.plugin.api.*
import com.epam.kodux.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.protobuf.*
import mu.*
import kotlin.time.*

internal class AgentDataCache {

    private val _data = atomic(persistentMapOf<String, AgentData>())

    operator fun get(key: String): AgentData? = _data.value[key]

    operator fun set(key: String, value: AgentData) {
        _data.update {
            it.put(key, value)
        }
    }

    fun getOrPut(
        key: String,
        provider: () -> AgentData
    ): AgentData = _data.updateAndGet {
        if (key !in it) {
            it.put(key, provider())
        } else it
    }[key]!!
}

internal class AgentData(
    val agentId: String,
    agentStores: AgentStores,
    initialSettings: SystemSettingsDto
) : AdminData {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    val buildManager get() = _buildManager.value

    override val classBytes: Map<String, ByteArray> get() = _classBytes.value

    val settings: SystemSettingsDto get() = _settings.value

    private val storeClient by lazy { agentStores[agentId] }

    private val _buildManager = atomic(AgentBuildManager(agentId))

    private val _classBytes = atomic(emptyMap<String, ByteArray>())

    private val _settings = atomic(initialSettings)

    suspend fun initBuild(version: String): Boolean {
        if (buildManager.agentBuilds.none()) {
            loadStoredData()
        }
        return (buildManager[version] == null).also {
            val agentBuild = buildManager.init(version)
            store(agentBuild)
        }
    }

    internal suspend fun initClasses() {
        val addedClasses: List<ByteArray> = buildManager.collectClasses()
        logger.debug { "Saving ${addedClasses.size} classes..." }
        measureTime {
            val classBytes: Map<String, ByteArray> = addedClasses.asSequence().map {
                ProtoBuf.load(ByteClass.serializer(), it)
            }.associate { it.className to it.bytes }
            _classBytes.value = classBytes
            storeClient.storeClasses(agentId, classBytes)
        }.let { duration -> logger.debug { "Saved ${addedClasses.size} classes in $duration." } }
    }

    suspend fun updateSettings(
        settings: SystemSettingsDto,
        block: suspend (SystemSettingsDto) -> Unit = {}
    ) {
        val current = this.settings
        if (current != settings) {
            _settings.value = settings
            storeClient.store(toSummary())
            block(current)
        }
    }

    suspend fun store(agentBuild: AgentBuild) = agentBuild.run {
        logger.debug { "Saving build ${agentBuild.id}..." }
        val buildData = AgentBuildData(
            id = id,
            agentId = id.agentId,
            detectedAt = detectedAt
        )
        measureTime {
            storeClient.executeInAsyncTransaction {
                store(toSummary())
                store(buildData)
            }
        }.let { duration -> logger.debug { "Saved build ${agentBuild.id} in $duration." } }

        logger.debug { "Saved build ${agentBuild.id}." }
    }

    private suspend fun loadStoredData() = storeClient.findById<AgentDataSummary>(agentId)?.let { summary ->
        logger.debug { "Loading data for $agentId..." }
        _settings.value = summary.settings
        val classBytes: Map<String, ByteArray> = storeClient.loadClasses(agentId) ?: emptyMap()
        _classBytes.value = classBytes
        val builds: List<AgentBuild> = storeClient.findBy<AgentBuildData> {
            AgentBuildData::agentId eq agentId
        }.map { data ->
            data.run {
                AgentBuild(
                    id = id,
                    agentId = agentId,
                    detectedAt = detectedAt,
                    info = BuildInfo(
                        version = id.version
                    )
                )
            }
        }
        _buildManager.value = AgentBuildManager(
            agentId = agentId,
            builds = builds
        )
        logger.debug { "Loaded data for $agentId" }
    }

    private fun toSummary() = AgentDataSummary(
        agentId = agentId,
        settings = settings
    )
}

private suspend fun StoreClient.storeClasses(
    agentId: String,
    classBytes: Map<String, ByteArray>
) {
    val storedData = StoredCodeData(
        id = agentId,
        data = ProtoBuf.dump(
            CodeData.serializer(),
            CodeData(classBytes = classBytes)
        ).let(Zstd::compress)
    )
    store(storedData)
}

private suspend fun StoreClient.loadClasses(
    agentId: String
): Map<String, ByteArray>? = findById<StoredCodeData>(agentId)?.run {
    ProtoBuf.load(CodeData.serializer(), Zstd.decompress(data)).classBytes
}
