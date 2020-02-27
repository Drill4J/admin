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
    private val _buildManager = atomic(AgentBuildManager(agentId, storeClient))

    private val _packagesPrefixes = atomic(readPackages())

    override val buildManager = _buildManager.value

    var packagesPrefixes: List<String>
        get() = _packagesPrefixes.value
        set(value) {
            _packagesPrefixes.value = value
        }

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

                val builds = storeClient.findBy<StorableBuildInfo> {
                    StorableBuildInfo::agentId eq agentId
                }.map(StorableBuildInfo::toBuildInfo)
                _buildManager.value = AgentBuildManager(
                    agentId = agentId,
                    storeClient = storeClient,
                    builds = builds,
                    lastBuild = summary.lastBuild
                )
            }
        }
    }

    suspend fun resetBuilds() {
        storeClient.deleteBy<StorableBuildInfo> { StorableBuildInfo::agentId eq agentId }
        _buildManager.value = AgentBuildManager(agentId, storeClient)
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
    @Id val agentId: String,
    val packagesPrefixes: List<String>,
    val lastBuild: String
)

@Serializable
data class StorableBuildInfo(
    @Id val id: String,
    val agentId: String,
    val version: String = "",
    val parentVersion: String = "",
    val methodChanges: MethodChanges = MethodChanges(),
    val classesBytes: Map<String, ByteArray> = emptyMap(),
    val javaMethods: Map<String, List<Method>> = emptyMap(),
    val new: Boolean
) {
    fun toBuildInfo() = BuildInfo(
        version = version,
        parentVersion = parentVersion,
        methodChanges = methodChanges,
        classesBytes = classesBytes,
        javaMethods = javaMethods,
        new = new
    )
}

fun BuildInfo.toStorable(agentId: String) = StorableBuildInfo(
    id = "$agentId:$version",
    agentId = agentId,
    version = version,
    parentVersion = parentVersion,
    methodChanges = methodChanges,
    classesBytes = classesBytes,
    javaMethods = javaMethods,
    new = new
)
