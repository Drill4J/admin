package com.epam.drill.admin.admindata

import com.epam.drill.admin.build.*
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

class AgentBuildManager(val agentId: String, private val storeClient: StoreClient, lastBuild: String = "") : BuildManager {
    private val _lastBuild = atomic(lastBuild)

    override val buildInfos: MutableMap<String, BuildInfo> = ConcurrentHashMap()

    var lastBuild: String
        get() = _lastBuild.value
        private set(value) {
            _lastBuild.value = value
        }

    @Deprecated("This field should be removed after applying appropriate changes to API")
    override val summaries: List<BuildSummaryWebSocket>
        get() = emptyList()

    val buildSummaries: List<BuildSummaryDto>
        get() = buildInfos.values.map { it.toBuildSummaryDto() }

    override operator fun get(buildVersion: String) = buildInfos[buildVersion]

    fun setupBuildInfo(buildVersion: String) {
        val buildVersionIsNew = buildInfos[buildVersion] == null || buildInfos[buildVersion]!!.new
        val buildInfo = buildInfos[buildVersion]
            ?: BuildInfo(buildVersion = buildVersion)
        val prevBuild = buildInfo.prevBuild
        buildInfos[buildVersion] = buildInfo.copy(
            buildVersion = buildVersion,
            classesBytes = emptyMap(),
            prevBuild = if (buildVersionIsNew) lastBuild else prevBuild
        )
        if (buildVersionIsNew) {
            lastBuild = buildVersion
            buildInfos[buildVersion] = buildInfos[buildVersion]!!.copy(new = false)
        }
    }

    fun addClass(buildVersion: String, rawData: String) {
        val buildInfo = buildInfos[buildVersion] ?: BuildInfo(buildVersion = buildVersion)
        val currentClasses = buildInfo.classesBytes
        val base64Class = Base64Class.serializer() parse rawData
        buildInfos[buildVersion] = buildInfo.copy(
            classesBytes = currentClasses + (base64Class.className to decode(base64Class.encodedBytes))
        )
    }

    suspend fun compareToPrev(buildVersion: String) {
        val currentMethods = buildInfos[buildVersion]?.classesBytes?.mapValues { (className, bytes) ->
            BcelClassParser(bytes, className).parseToJavaMethods()
        } ?: emptyMap()
        buildInfos[buildVersion] = buildInfos[buildVersion]?.copy(javaMethods = currentMethods)
            ?: BuildInfo(buildVersion = buildVersion)
        val buildInfo = buildInfos[buildVersion] ?: BuildInfo(buildVersion = buildVersion)
        val prevMethods = buildInfos[buildInfo.prevBuild]?.javaMethods ?: mapOf()

        val coverageBuilder = CoverageBuilder()
        val analyzer = Analyzer(ExecutionDataStore(), coverageBuilder)
        buildInfos[buildVersion]?.classesBytes?.map { (className, bytes) -> analyzer.analyzeClass(bytes, className) }
        val bundleCoverage = coverageBuilder.getBundle("")

        buildInfos[buildVersion] = buildInfos[buildVersion]?.copy(
            methodChanges = MethodsComparator(bundleCoverage).compareClasses(prevMethods, currentMethods)
        ) ?: BuildInfo(buildVersion = buildVersion)

        val processedBuildInfo = buildInfos[buildVersion]!!
        storeClient.store(processedBuildInfo.toStorable(agentId))
    }

}

fun decode(source: String): ByteArray = java.util.Base64.getDecoder().decode(source)
