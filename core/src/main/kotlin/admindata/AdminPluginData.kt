package com.epam.drill.admin.admindata

import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import com.epam.kodux.*
import kotlinx.atomicfu.*
import kotlinx.serialization.Serializable
import mu.*
import java.io.*
import java.util.*
import java.util.concurrent.*

const val APP_CONFIG = ".application.properties"

private val logger = KotlinLogging.logger {}

typealias AdminDataVault = ConcurrentHashMap<String, AdminPluginData>

class AdminPluginData(
    val agentId: String,
    private val storeClient: StoreClient,
    private val devMode: Boolean
) : AdminData {

    private var _packagesPrefixes = atomic(readPackages())

    var packagesPrefixes: List<String>
        get() = _packagesPrefixes.value
        set(value) {
            _packagesPrefixes.value = value
        }

    override var buildManager = AgentBuildManager(agentId, storeClient)

    private fun readPackages(): List<String> = Properties().run {
        val propertiesFileName = if (devMode) "dev$APP_CONFIG" else "prod$APP_CONFIG"
        getPackagesProperty(propertiesFileName)
    }

    private fun Properties.getPackagesProperty(fileName: String): List<String> = try {
        load(AdminPluginData::class.java.getResourceAsStream("/$fileName"))
        getProperty("prefixes").split(",")
    } catch (ioe: IOException) {
        logger.error(ioe) { "Could not open properties file; packages prefixes are empty" }
        emptyList()
    } catch (ise: IllegalStateException) {
        logger.error(ise) { "Could not read 'prefixes' property; packages prefixes are empty" }
        emptyList()
    }

    suspend fun loadStoredData() {
        storeClient.findById<AdminDataSummary>(agentId).let { summary ->
            if (summary != null) {
                packagesPrefixes = summary.packagesPrefixes
                buildManager = AgentBuildManager(agentId, storeClient, summary.lastBuild)
                storeClient.findBy<StorableBuildInfo> { StorableBuildInfo::agentId eq agentId }
                    .forEach {
                        buildManager.buildInfos[it.buildVersion] = it.toBuildInfo() }
            }
        }
    }

    suspend fun resetBuilds() {
        storeClient.deleteBy<StorableBuildInfo> { StorableBuildInfo::agentId eq agentId }
        buildManager = AgentBuildManager(agentId, storeClient)
        refreshStoredSummary()
    }

    suspend fun refreshStoredSummary() {
        storeClient.store(
            AdminDataSummary(
                agentId = agentId,
                packagesPrefixes = packagesPrefixes,
                lastBuild = buildManager.lastBuild
            )
        )
    }
}

@Serializable
data class AdminDataSummary(
    @Id
    val agentId: String,
    val packagesPrefixes: List<String>,
    val lastBuild: String
)

@Serializable
data class StorableBuildInfo(
    @Id
    val id: String,
    val agentId: String,
    val buildVersion: String = "",
    val prevBuild: String = "",
    val methodChanges: MethodChanges = MethodChanges(),
    val classesBytes: Map<String, ByteArray> = emptyMap(),
    val javaMethods: Map<String, List<Method>> = emptyMap(),
    val new: Boolean
) {
    fun toBuildInfo() = BuildInfo(
        buildVersion = buildVersion,
        prevBuild = prevBuild,
        methodChanges = methodChanges,
        classesBytes = classesBytes,
        javaMethods = javaMethods,
        new = new
    )
}

fun BuildInfo.toStorable(agentId: String) = StorableBuildInfo(
    id = "$agentId:$buildVersion",
    agentId = agentId,
    buildVersion = buildVersion,
    prevBuild = prevBuild,
    methodChanges = methodChanges,
    classesBytes = classesBytes,
    javaMethods = javaMethods,
    new = new
)
