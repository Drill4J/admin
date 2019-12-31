package com.epam.drill.admindata

import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import com.epam.drill.util.*
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

    override val summaries: List<BuildSummaryWebSocket>
        get() = buildInfos.values.map { it.buildSummary.toWebSocketSummary(it.buildAlias) }

    private val buildVersions: Map<String, String>
        get() = buildInfos.map { (buildVersion, buildInfo) ->
            buildVersion to buildInfo.buildAlias
        }.toMap()

    val buildVersionsJson = buildVersions.map { (id, name) ->
        AgentBuildVersionJson(id, name)
    }

    override operator fun get(buildVersion: String) = buildInfos[buildVersion]

    fun setupBuildInfo(buildVersion: String, currentAlias: String) {
        buildInfos[buildVersion] = buildInfo(buildVersion).copy(
            buildAlias = currentAlias,
            classesBytes = emptyMap()
        )
    }

    fun addClass(buildVersion: String, rawData: String) {
        val buildInfo = buildInfo(buildVersion)
        val currentClasses = buildInfo.classesBytes
        val base64Class = Base64Class.serializer() parse rawData
        buildInfos[buildVersion] = buildInfo.copy(
            classesBytes = currentClasses + (base64Class.className to decode(base64Class.encodedBytes))
        )
    }

    suspend fun processBuild(notificationsManager: NotificationsManager, agentInfo: AgentInfo) {
        val buildVersion = agentInfo.buildVersion
        val buildInfo = buildInfo(buildVersion)
        val buildVersionIsNew = buildInfo.new
        val prevBuild = if (buildVersionIsNew) lastBuild else buildInfo.prevBuild

        val currentMethods = buildInfo.classesBytes.mapValues { (className, bytes) ->
            BcelClassParser(bytes, className).parseToJavaMethods()
        }
        val methodChanges = buildInfo.compareToBuild(prevBuild, currentMethods)
        val buildSummary = buildInfo.buildSummary(methodChanges)

        buildInfos[buildVersion] = buildInfo.copy(
            prevBuild = prevBuild,
            javaMethods = currentMethods,
            methodChanges = methodChanges,
            buildSummary = buildSummary
        )

        if (buildVersionIsNew) {
            notificationsManager.newBuildNotify(agentInfo)
            lastBuild = buildVersion
            buildInfos[buildVersion] = buildInfo(buildVersion).copy(new = false)
        }

        storeClient.store(buildInfo(buildVersion).toStorable(agentId))
    }

    private fun buildInfo(buildVersion: String) = buildInfos[buildVersion] ?: BuildInfo(buildVersion = buildVersion)

    private fun BuildInfo.compareToBuild(buildVersion: String, currentMethods: Map<String, Methods>): MethodChanges {
        val prevMethods = buildInfo(buildVersion).javaMethods
        val coverageBuilder = CoverageBuilder()
        val analyzer = Analyzer(ExecutionDataStore(), coverageBuilder)
        classesBytes.map { (className, bytes) -> analyzer.analyzeClass(bytes, className) }
        val bundleCoverage = coverageBuilder.getBundle("")
        return MethodsComparator(bundleCoverage).compareClasses(prevMethods, currentMethods)
    }

    private fun BuildInfo.buildSummary(methodChanges: MethodChanges): BuildSummary {
        val deletedMethodsCount = methodChanges.map[DiffType.DELETED]?.count() ?: 0
        val totalMethods = methodChanges.map.values.flatten().count() - deletedMethodsCount
        val newMethods = methodChanges.map[DiffType.NEW]?.count() ?: 0
        val modifiedMethods = (methodChanges.map[DiffType.MODIFIED_NAME]?.count() ?: 0) +
                (methodChanges.map[DiffType.MODIFIED_BODY]?.count() ?: 0) +
                (methodChanges.map[DiffType.MODIFIED_DESC]?.count() ?: 0)
        val unaffectedMethods = methodChanges.map[DiffType.UNAFFECTED]?.count() ?: 0
        return BuildSummary(
            buildVersion = buildVersion,
            addedDate = System.currentTimeMillis(),
            totalMethods = totalMethods,
            newMethods = newMethods,
            modifiedMethods = modifiedMethods,
            unaffectedMethods = unaffectedMethods,
            deletedMethods = deletedMethodsCount
        )
    }

    suspend fun renameBuild(buildVersion: AgentBuildVersionJson) {
        val buildInfo = buildInfos[buildVersion.id]?.copy(buildAlias = buildVersion.name)
            ?: BuildInfo(buildVersion = buildVersion.id, buildAlias = buildVersion.name)
        buildInfos[buildVersion.id] = buildInfo
        storeClient.store(buildInfo.toStorable(agentId))
    }

    fun buildAliasExists(alias: String) = buildInfos.any { it.value.buildAlias == alias }

}

fun decode(source: String): ByteArray = java.util.Base64.getDecoder().decode(source)