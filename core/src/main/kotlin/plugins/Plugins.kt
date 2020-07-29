package com.epam.drill.admin.plugins

import com.epam.drill.common.*
import com.epam.drill.plugin.api.end.*
import java.io.*

class Plugins(
    private val plugins: MutableMap<String, Plugin> = mutableMapOf()
) : Map<String, Plugin> by plugins {
    operator fun set(k: String, v: Plugin) = plugins.put(k, v)
}

data class Plugin(
    val pluginClass: Class<AdminPluginPart<*>>,
    val agentPartFiles: AgentPartFiles,
    val pluginBean: PluginMetadata,
    val version: String = ""
)

data class AgentPartFiles(
    val jar: File,
    val windowsPart: File? = null,
    val linuxPart: File? = null
)

val Plugin.agentPluginPart: File
    get() = agentPartFiles.jar
val Plugin.windowsPart: File?
    get() = agentPartFiles.windowsPart
val Plugin.linuxPar: File?
    get() = agentPartFiles.linuxPart
