/**
 * Copyright 2020 - 2022 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.drill.admin.plugins

import com.epam.drill.common.*
import com.epam.drill.plugin.api.end.*
import java.io.*

/**
 * Collection of plugins
 */
class Plugins(
    private val plugins: MutableMap<String, Plugin> = mutableMapOf(),
) : Map<String, Plugin> by plugins {
    operator fun set(k: String, v: Plugin) = plugins.put(k, v)
}

/**
 * Plugin structure
 *
 * @param pluginClass the java class of the plugin
 * @param agentPartFiles the plugin files location
 * @param pluginBean the plugin metadata
 * @param version the version of the plugin
 */
data class Plugin(
    val pluginClass: Class<AdminPluginPart<*>>,
    val agentPartFiles: AgentPartFiles,
    val pluginBean: PluginMetadata,
    val version: String = "",
)

/**
 * Agent part structure
 */
data class AgentPartFiles(
    val jar: File,
    val windowsPart: File? = null,
    val linuxPart: File? = null,
)

const val PLUGIN_PACKAGE = "com.epam.drill.plugins"

val Plugin.agentPluginPart: File
    get() = agentPartFiles.jar
val Plugin.windowsPart: File?
    get() = agentPartFiles.windowsPart
val Plugin.linuxPar: File?
    get() = agentPartFiles.linuxPart
