package com.epam.drill.admin.admindata

import com.epam.drill.admin.build.*
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

    override val buildManager get() = _buildManager.value

    var packagesPrefixes: List<String>
        get() = _packagesPrefixes.value
        set(value) {
            _packagesPrefixes.value = value
        }

    private val _buildManager = atomic(AgentBuildManager(agentId, storeClient))

    private val _packagesPrefixes = atomic(readPackages())

    suspend fun loadStoredData() {
        storeClient.findById<AdminDataSummary>(agentId).let { summary ->
            if (summary != null) {
                packagesPrefixes = summary.packagesPrefixes

                val builds = storeClient.findBy<AgentBuild> {
                    AgentBuild::agentId eq agentId
                }
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
        storeClient.deleteBy<AgentBuild> { AgentBuild::agentId eq agentId }
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

    private fun readPackages(): List<String> = Properties().run {
        val propertiesFileName = if (devMode) "dev$APP_CONFIG" else "prod$APP_CONFIG"
        getPackagesProperty(propertiesFileName)
    }

    private fun Properties.getPackagesProperty(fileName: String): List<String> = try {
        load(AdminPluginData::class.java.getResourceAsStream("/$fileName"))
        getProperty("prefixes").split(",")
    } catch (e: Exception) {
        when (e) {
            is IOException -> logger.error(e) { "Could not open properties file; packages prefixes are empty" }
            is IllegalStateException -> logger.error(e) { "Could not read 'prefixes' property; packages prefixes are empty" }
            else -> throw e
        }
        emptyList()
    }

}

@Serializable
data class AdminDataSummary(
    @Id val agentId: String,
    val packagesPrefixes: List<String>,
    val lastBuild: String
)
