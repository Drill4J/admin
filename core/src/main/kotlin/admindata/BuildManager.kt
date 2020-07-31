package com.epam.drill.admin.admindata

import com.epam.drill.admin.build.*
import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.protobuf.*

class AgentBuildManager(
    val agentId: String,
    builds: Iterable<AgentBuild> = emptyList(),
    lastBuild: String = ""
) : BuildManager {

    override val builds: Collection<BuildInfo>
        get() = buildMap.values.map { it.info }

    internal val agentBuilds get() = buildMap.values

    val lastBuild: String get() = _lastBuild.value

    private val buildMap: PersistentMap<String, AgentBuild>
        get() = _buildMap.value

    private val _lastBuild = atomic(lastBuild)

    private val _addedClasses = atomic(persistentListOf<ByteArray>())

    private val _buildMap = atomic(
        builds.associateBy { it.info.version }.toPersistentMap()
    )

    override operator fun get(version: String) = buildMap[version]?.info

    fun init(version: String) = _buildMap.updateAndGet { map ->
        if (version !in map) {
            val build = AgentBuild(
                id = AgentBuildId(agentId, version),
                agentId = agentId,
                info = BuildInfo(
                    version = version,
                    parentVersion = _lastBuild.getAndUpdate { version }
                ),
                detectedAt = System.currentTimeMillis()
            )
            map.mutate {
                map.forEach { (k, v) ->
                    it[k] = v.copy(info = v.info.copy(classesBytes = emptyMap()))
                }
                it[version] = build
            }
        } else map
    }.getValue(version)

    fun addClass(rawData: ByteArray) = _addedClasses.update { it + rawData }

    fun initClasses(buildVersion: String): AgentBuild = init(buildVersion).let { build ->
        val addedClasses: List<ByteArray> = _addedClasses.getAndUpdate { persistentListOf() }
        val classBytes = addedClasses.asSequence().map {
            ProtoBuf.load(ByteClass.serializer(), it)
        }.associate { it.className to it.bytes }
        val buildWithData = build.copy(
            info = build.info.copy(classesBytes = classBytes)
        )
        _buildMap.update { map -> map.put(buildVersion, buildWithData) }
        buildWithData
    }
}
