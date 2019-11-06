package com.epam.drill.admindata

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

class AgentBuildManager(val agentId: String, val storeClient: StoreClient, lastBuild: String = "") : BuildManager {
    private val _lastBuild = atomic(lastBuild)

    override val buildInfos: MutableMap<String, BuildInfo> = ConcurrentHashMap()

    var lastBuild: String
        get() = _lastBuild.value
        private set(value) {
            _lastBuild.value = value
        }

    override val summaries: List<BuildSummary>
        get() = buildInfos.values.map { it.buildSummary }

    override operator fun get(buildVersion: String) = buildInfos[buildVersion]

    fun setupBuildInfo(buildVersion: String) {
        val buildVersionIsNew = buildInfos[buildVersion] == null
        val buildInfo = buildInfos[buildVersion] ?: BuildInfo(agentId)
        val prevBuild = buildInfo.prevBuild
        buildInfos[buildVersion] = buildInfo.copy(
            buildVersion = buildVersion,
            classesBytes = emptyMap(),
            prevBuild = if (buildVersionIsNew) lastBuild else prevBuild
        )
        if (buildVersionIsNew) {
            lastBuild = buildVersion
        }
    }

    fun addClass(buildVersion: String, rawData: String) {
        val buildInfo = buildInfos[buildVersion] ?: BuildInfo(agentId)
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
        buildInfos[buildVersion] = buildInfos[buildVersion]?.copy(javaMethods = currentMethods) ?: BuildInfo(agentId)
        val buildInfo = buildInfos[buildVersion] ?: BuildInfo(agentId)
        val prevMethods = buildInfos[buildInfo.prevBuild]?.javaMethods ?: mapOf()

        val coverageBuilder = CoverageBuilder()
        val analyzer = Analyzer(ExecutionDataStore(), coverageBuilder)
        buildInfos[buildVersion]?.classesBytes?.map { (className, bytes) -> analyzer.analyzeClass(bytes, className) }
        val bundleCoverage = coverageBuilder.getBundle("")

        buildInfos[buildVersion] = buildInfos[buildVersion]?.copy(
            methodChanges = MethodsComparator(bundleCoverage).compareClasses(prevMethods, currentMethods)
        ) ?: BuildInfo(agentId)

        val changes = buildInfos[buildVersion]?.methodChanges?.map ?: emptyMap()
        val deletedMethodsCount = changes[DiffType.DELETED]?.count() ?: 0
        val processedBuildInfo = buildInfos[buildVersion]?.copy(
            buildSummary = BuildSummary(
                name = buildVersion,
                addedDate = System.currentTimeMillis(),
                totalMethods = changes.values.flatten().count() - deletedMethodsCount,
                newMethods = changes[DiffType.NEW]?.count() ?: 0,
                modifiedMethods = (changes[DiffType.MODIFIED_NAME]?.count() ?: 0) +
                        (changes[DiffType.MODIFIED_BODY]?.count() ?: 0) +
                        (changes[DiffType.MODIFIED_DESC]?.count() ?: 0),
                unaffectedMethods = changes[DiffType.UNAFFECTED]?.count() ?: 0,
                deletedMethods = deletedMethodsCount
            )
        ) ?: BuildInfo(agentId)
        storeClient.store(processedBuildInfo.toStorable(agentId))
        buildInfos[buildVersion] = processedBuildInfo
    }

}

fun decode(source: String): ByteArray = java.util.Base64.getDecoder().decode(source)