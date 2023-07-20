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
import com.epam.drill.plugins.test2code.Plugin as Test2CodePlugin

/**
 * Collection of plugins
 */
class Plugins(
    private val plugins: Map<String, Plugin> = mutableMapOf(),
) : Map<String, Plugin> by plugins {
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
    val pluginClass: Class<out AdminPluginPart<*>>,
    val pluginBean: PluginMetadata,
    val version: String = "",
)

/**
 * Embedded test2code plugin
 */
fun test2CodePlugin(): Plugin {
    val pluginId = "test2code"
    return Plugin(
        pluginClass = Test2CodePlugin::class.java,
        pluginBean = PluginMetadata(
            id = pluginId,
            name = "Test2Code",
            description = "Test2Code plugin minimizes your regression suite via Test Impact Analytics by suggesting only affected subset of tests to run, and highlight  untested areas via Test Gap Analysis, providing evidence of how changes are tested and which areas and not tested at all.",
            type = "Custom",
            config = "{\"message\": \"hello from default plugin config... This is 'plugin_config.json file\"}"
        ),
        version = "version"
    )
}