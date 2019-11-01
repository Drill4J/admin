package com.epam.drill.admindata

import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import kotlinx.atomicfu.*
import mu.*
import java.io.*
import java.util.*
import java.util.concurrent.*

const val APP_CONFIG = ".application.properties"

private val logger = KotlinLogging.logger {}

typealias AdminDataVault = ConcurrentHashMap<String, AdminPluginData>

class AdminPluginData(val agentId: String, private val devMode: Boolean) : AdminData {

    private var _packagesPrefixes = atomic(PackagesPrefixes(readPackages()))

    var packagesPrefixes: PackagesPrefixes
        get() = _packagesPrefixes.value
        set(value) {
            _packagesPrefixes.value = value
        }

    override var buildManager = AgentBuildManager(agentId)

    private fun readPackages(): List<String> = Properties().run {
        val propertiesFileName = if (devMode) "dev$APP_CONFIG" else "prod$APP_CONFIG"
        getPackagesProperty(propertiesFileName)
    }

    private fun Properties.getPackagesProperty(fileName: String): List<String> = try {
        load(AdminPluginData::class.java.getResourceAsStream("/$fileName"))
        getProperty("prefixes").split(",")
    } catch (ioe: IOException) {
        logger.error("Could not open properties file; packages prefixes are empty")
        emptyList()
    } catch (ise: IllegalStateException) {
        logger.error("Could not read 'prefixes' property; packages prefixes are empty")
        emptyList()
    }
}