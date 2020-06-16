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

    val lastBuild: String get() = _lastBuild.value

    private val buildMap: PersistentMap<String, AgentBuild>
        get() = _buildMap.value

    private val _lastBuild = atomic(lastBuild)

    private val _addedClasses = atomic(persistentListOf<ByteArray>())

    private val _buildMap = atomic(
        builds.associateBy { it.info.version }.toPersistentMap()
    )

    override operator fun get(version: String) = buildMap[version]?.info

    fun dtoList() = buildMap.values.map(AgentBuild::toBuildSummaryDto)

    fun initBuildInfo(version: String) = _buildMap.update { map ->
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
            map.put(version, build)
        } else map
    }

    fun addClass(rawData: ByteArray) = _addedClasses.update { it + rawData }

    fun initClasses(buildVersion: String): AgentBuild = buildMap[buildVersion]?.let { build ->
        val addedClasses: List<ByteArray> = _addedClasses.getAndUpdate { persistentListOf() }
        val classBytesSeq = addedClasses.asSequence().map {
            ProtoBuf.load(ByteClass.serializer(), it)
        }
        val bundleCoverage = classBytesSeq.bundle()
        val bundleClasses = bundleCoverage.packages.flatMap { it.classes }.associate { c ->
            c.name to c.methods.map { m -> m.name to m.desc }.toSet()
        }.filterValues { it.any() }
        val classBytes: Map<String, ByteArray> = classBytesSeq.filter { it.className in bundleClasses }.associate {
            it.className to it.bytes
        }
        val currentMethods: Map<String, List<Method>> = classBytes.mapValues { (name, bytes) ->
            val parsedClass = ParsedClass(name, bytes)
            val allowedMethods = bundleClasses.getValue(name)
            parsedClass.methods().filter { (it.name to it.desc) in allowedMethods }
        }
        val methodChanges = buildMap[build.info.parentVersion]?.run {
            bundleCoverage.compareClasses(info.javaMethods, currentMethods)
        } ?: MethodChanges()

        val buildWithData = build.copy(
            info = build.info.copy(
                classesBytes = classBytes,
                javaMethods = currentMethods,
                methodChanges = methodChanges
            )
        )
        _buildMap.update { map -> map.put(buildVersion, buildWithData) }
        buildWithData
    } ?: error("Agent build is not initialized! agentId=$agentId, buildVersion=$buildVersion")
}

fun Sequence<ByteClass>.bundle(): IBundleCoverage = CoverageBuilder().also { builder ->
    val analyzer = Analyzer(ExecutionDataStore(), builder)
    forEach { (className, bytes) ->
        analyzer.analyzeClass(bytes, className)
    }
}.getBundle("")
