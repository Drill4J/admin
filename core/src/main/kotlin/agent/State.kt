package com.epam.drill.admin.agent

import com.epam.drill.admin.admindata.*
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

internal class AgentDataCache {

    private val _data = atomic(persistentMapOf<String, AgentData>())

    operator fun get(key: String): AgentData? = _data.value[key]

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
    private val storeClient: StoreClient,
    defaultPackages: List<String>
) : AdminData {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override val buildManager get() = _buildManager.value

    override val classBytes: Map<String, ByteArray> get() = _classBytes.value

    val settings: SystemSettingsDto get() = _settings.value

    private val _buildManager = atomic(AgentBuildManager(agentId))

    private val _classBytes = atomic(emptyMap<String, ByteArray>())

    private val _settings = atomic(
        SystemSettingsDto(packages = defaultPackages)
    )

    suspend fun initBuild(version: String) {
        if (buildManager.builds.none()) {
            loadStoredData()
        }
        buildManager[version] ?: run {
            val agentBuild = buildManager.init(version)
            store(agentBuild)
        }
    }

    internal suspend fun initClasses() {
        kotlin.runCatching {  }
        val addedClasses: List<ByteArray> = buildManager.collectClasses()
        logger.debug { "Saving ${addedClasses.size} classes..." }
        measureTime {
            val classBytes = addedClasses.asSequence().map {
                ProtoBuf.load(ByteClass.serializer(), it)
            }.associate { it.className to it.bytes }
            _classBytes.value = classBytes
            val codeData = StoredCodeData(
                id = agentId,
                data = ProtoBuf.dump(CodeData.serializer(), CodeData(classBytes = classBytes))
            )
            storeClient.store(codeData)
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
            parentVersion = info.parentVersion,
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
        val classBytes: Map<String, ByteArray> = storeClient.findById<StoredCodeData>(agentId)?.let {
            ProtoBuf.load(CodeData.serializer(), it.data).classBytes
        } ?: emptyMap()
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
                        version = id.version,
                        parentVersion = parentVersion
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

    private fun toSummary(): AgentDataSummary =
        AgentDataSummary(
            agentId = agentId,
            settings = settings,
            lastBuild = buildManager.lastBuild
        )
}

@Serializable
internal data class AgentDataSummary(
    @Id val agentId: String,
    val settings: SystemSettingsDto,
    val lastBuild: String
)
