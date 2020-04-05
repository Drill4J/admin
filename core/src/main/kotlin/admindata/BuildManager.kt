package com.epam.drill.admin.admindata

import com.epam.drill.admin.build.*
import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import org.jacoco.core.analysis.*
import org.jacoco.core.data.*

class AgentBuildManager(
    val agentId: String,
    builds: Iterable<AgentBuild> = emptyList(),
    lastBuild: String = ""
) : BuildManager {

    override val builds: Collection<BuildInfo>
        get() = buildMap.values.map { it.info }

    var lastBuild: String
        get() = _lastBuild.value
        private set(value) {
            _lastBuild.value = value
        }

    private val _lastBuild = atomic(lastBuild)

    private val _addedClasses = atomic(persistentListOf<String>())

    private val _buildMap = atomic(builds.associateBy { it.info.version }.toPersistentMap())

    private val buildMap: PersistentMap<String, AgentBuild>
        get() = _buildMap.value

    override operator fun get(version: String) = buildMap[version]?.info

    fun dtoList() = buildMap.values.map(AgentBuild::toBuildSummaryDto)

    fun initBuildInfo(buildVersion: String) {
        val build = buildMap[buildVersion]
        val prevBuild = if (build == null) {
            val prev = lastBuild
            lastBuild = buildVersion
            prev
        } else build.info.parentVersion
        updateAndGet(buildVersion) {
            copy(
                info = info.copy(
                    classesBytes = emptyMap(),
                    parentVersion = prevBuild
                )
            )
        }
    }

    fun addClass(rawData: String) = _addedClasses.update { it + rawData }

    fun initClasses(buildVersion: String): AgentBuild = run {
        val addedClasses: List<String> = _addedClasses.getAndUpdate { persistentListOf() }
        val parsedClasses: Map<String, ParsedClass> = addedClasses.asSequence()
            .map { Base64Class.serializer() parse it }
            .map { ParsedClass(it.className, decode(it.encodedBytes)) }
            .filter { it.anyCode() }
            .associateBy { it.name }
        val classesBytes: Map<String, ByteArray> = parsedClasses.mapValues { it.value.bytes }
        val currentMethods: Map<String, List<Method>> = parsedClasses.mapValues { it.value.methods() }
        val build = updateAndGet(buildVersion) {
            copy(
                info = info.copy(
                    javaMethods = currentMethods,
                    classesBytes = classesBytes
                )
            )
        }
        val parentVersion = build.info.parentVersion
        val prevMethods = buildMap[parentVersion]?.info?.javaMethods ?: mapOf()
        val coverageBuilder = CoverageBuilder()
        val analyzer = Analyzer(ExecutionDataStore(), coverageBuilder)
        //TODO remove jacoco usage
        classesBytes.map { (className, bytes) ->
            analyzer.analyzeClass(bytes, className)
        }
        val bundleCoverage = coverageBuilder.getBundle("")
        updateAndGet(buildVersion) {
            copy(
                info = info.copy(
                    methodChanges = bundleCoverage.compareClasses(prevMethods, currentMethods)
                )
            )
        }
    }

    private fun updateAndGet(
        version: String,
        mutate: AgentBuild.() -> AgentBuild
    ): AgentBuild = _buildMap.updateAndGet { map ->
        val build = map[version] ?: AgentBuild(
            id = AgentBuildId(agentId = agentId, version = version),
            agentId = agentId,
            info = BuildInfo(
                version = version
            ),
            detectedAt = System.currentTimeMillis()
        )
        map.put(version, build.mutate())
    }[version]!!

}

fun decode(source: String): ByteArray = java.util.Base64.getDecoder().decode(source)
