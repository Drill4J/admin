package com.epam.drill.admin.admindata

import com.epam.drill.admin.build.*
import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.protobuf.*
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

    private val _addedClasses = atomic(persistentListOf<ByteArray>())

    private val _buildMap = atomic(
        builds.associateBy { it.info.version }.toPersistentMap()
    )

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

    fun addClass(rawData: ByteArray) = _addedClasses.update { it + rawData }

    fun initClasses(buildVersion: String): AgentBuild = run {
        val addedClasses: List<ByteArray> = _addedClasses.getAndUpdate { persistentListOf() }
        val classBytesSeq = addedClasses.asSequence().map {
            ProtoBuf.load(ByteClass.serializer(), it)
        }
        val bundleCoverage = classBytesSeq.bundle()
        val bundleClasses = bundleCoverage.packages.flatMap { it.classes }.associate { c ->
            c.name to c.methods.map { m -> m.name to m.desc }.toSet()
        }
        val classBytes: Map<String, ByteArray> = classBytesSeq.filter { it.className in bundleClasses }.associate {
            it.className to it.bytes
        }
        val currentMethods: Map<String, List<Method>> = classBytes.mapValues { (name, bytes) ->
            val parsedClass = ParsedClass(name, bytes)
            val allowedMethods = bundleClasses.getValue(name)
            parsedClass.methods().filter { (it.name to it.desc) in allowedMethods }
        }
        val build = updateAndGet(buildVersion) {
            copy(
                info = info.copy(
                    classesBytes = classBytes,
                    javaMethods = currentMethods
                )
            )
        }
        val parentVersion = build.info.parentVersion
        val prevMethods = buildMap[parentVersion]?.info?.javaMethods ?: mapOf()
        bundleCoverage.packages.flatMap { it.classes }
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

fun Sequence<ByteClass>.bundle(): IBundleCoverage = CoverageBuilder().also { builder ->
    val analyzer = Analyzer(ExecutionDataStore(), builder)
    forEach { (className, bytes) ->
        analyzer.analyzeClass(bytes, className)
    }
}.getBundle("")
