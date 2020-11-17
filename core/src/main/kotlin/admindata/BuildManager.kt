package com.epam.drill.admin.admindata

import com.epam.drill.admin.build.*
import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*

class AgentBuildManager(
    val agentId: String,
    builds: Iterable<AgentBuild> = emptyList()
) : BuildManager {

    override val builds: Collection<BuildInfo>
        get() = buildMap.values.map { it.info }

    internal val agentBuilds get() = buildMap.values

    private val buildMap: PersistentMap<String, AgentBuild>
        get() = _buildMap.value

    private val _addedClasses = atomic(persistentListOf<ByteArray>())

    private val _buildMap = atomic(
        builds.associateBy { it.info.version }.toPersistentMap()
    )

    override operator fun get(version: String) = buildMap[version]?.info

    internal fun init(version: String) = _buildMap.updateAndGet { map ->
        if (version !in map) {
            val build = AgentBuild(
                id = AgentBuildId(agentId, version),
                agentId = agentId,
                info = BuildInfo(
                    version = version
                ),
                detectedAt = System.currentTimeMillis()
            )
            map.put(version, build)
        } else map
    }.getValue(version)

    internal fun addClass(rawData: ByteArray) = _addedClasses.update { it + rawData }

    internal fun collectClasses(): List<ByteArray> = _addedClasses.getAndUpdate { persistentListOf() }
}
