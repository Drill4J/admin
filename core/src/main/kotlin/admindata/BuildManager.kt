package com.epam.drill.admin.admindata

import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import com.epam.kodux.*
import kotlinx.atomicfu.*
import org.jacoco.core.analysis.*
import org.jacoco.core.data.*
import java.util.concurrent.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

class AgentBuildManager(
    val agentId: String,
    private val storeClient: StoreClient,
    builds: Iterable<BuildInfo> = emptyList(),
    lastBuild: String = ""
) : BuildManager {

    private val _lastBuild = atomic(lastBuild)

    private val buildMap = ConcurrentHashMap<String, BuildInfo>().apply {
        builds.forEach { put(it.version, it) }
    }

    override val builds: Collection<BuildInfo>
        get() = buildMap.values

    @Suppress("OverridingDeprecatedMember")
    override val buildInfos = buildMap

    var lastBuild: String
        get() = _lastBuild.value
        private set(value) {
            _lastBuild.value = value
        }

    override operator fun get(version: String) = buildMap[version]

    fun setupBuildInfo(buildVersion: String) {
        val buildVersionIsNew = buildMap[buildVersion] == null || buildMap[buildVersion]!!.new
        val buildInfo = buildMap[buildVersion]
            ?: BuildInfo(version = buildVersion)
        val prevBuild = buildInfo.parentVersion
        buildMap[buildVersion] = buildInfo.copy(
            version = buildVersion,
            parentVersion = if (buildVersionIsNew) lastBuild else prevBuild,
            classesBytes = emptyMap()
        )
        if (buildVersionIsNew) {
            lastBuild = buildVersion
            buildMap[buildVersion] = buildMap[buildVersion]!!.copy(new = false)
        }
    }

    fun addClass(buildVersion: String, rawData: String) {
        val buildInfo = buildMap[buildVersion] ?: BuildInfo(version = buildVersion)
        val currentClasses = buildInfo.classesBytes
        val base64Class = Base64Class.serializer() parse rawData
        buildMap[buildVersion] = buildInfo.copy(
            classesBytes = currentClasses + (base64Class.className to decode(base64Class.encodedBytes))
        )
    }

    suspend fun compareToPrev(buildVersion: String) {
        val currentMethods = buildMap[buildVersion]?.classesBytes?.mapValues { (className, bytes) ->
            BcelClassParser(bytes, className).parseToJavaMethods()
        } ?: emptyMap()
        buildMap[buildVersion] = buildMap[buildVersion]?.copy(javaMethods = currentMethods)
            ?: BuildInfo(version = buildVersion)
        val buildInfo = buildMap[buildVersion] ?: BuildInfo(version = buildVersion)
        val prevMethods = buildMap[buildInfo.parentVersion]?.javaMethods ?: mapOf()

        val coverageBuilder = CoverageBuilder()
        val analyzer = Analyzer(ExecutionDataStore(), coverageBuilder)
        buildMap[buildVersion]?.classesBytes?.map { (className, bytes) -> analyzer.analyzeClass(bytes, className) }
        val bundleCoverage = coverageBuilder.getBundle("")

        buildMap[buildVersion] = buildMap[buildVersion]?.copy(
            methodChanges = MethodsComparator(bundleCoverage).compareClasses(prevMethods, currentMethods)
        ) ?: BuildInfo(version = buildVersion)

        val processedBuildInfo = buildMap[buildVersion]!!
        storeClient.store(processedBuildInfo.toStorable(agentId))
    }

}

fun decode(source: String): ByteArray = java.util.Base64.getDecoder().decode(source)
