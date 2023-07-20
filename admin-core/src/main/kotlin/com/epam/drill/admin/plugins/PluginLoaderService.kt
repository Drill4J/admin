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


import com.epam.drill.admin.config.*
import com.epam.drill.common.*
import com.epam.dsm.util.id
import io.ktor.application.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import mu.*
import java.io.*
import java.lang.System.*
import java.util.jar.JarFile
import java.util.zip.*
import com.epam.drill.plugins.test2code.Plugin as Test2CodePlugin

private val logger = KotlinLogging.logger {}

const val TEST2CODE = "test2code"
val defaultPlugins = setOf(TEST2CODE)

/**
 * Service for loading plugins
 */
class PluginLoaderService(
    private val application: Application,
    private val workDir: File = File(getenv("DRILL_HOME"), "work"),
    val plugins: Plugins = Plugins(),
) {

    private val pluginStoragePath = File("distr").resolve("adminStorage")
    private val pluginPaths: List<File> = listOf(pluginStoragePath).map { it.canonicalFile }

    init {
        runBlocking(Dispatchers.Default) {
            pluginStoragePath.mkdirs()
            try {
                logger.info { "Searching for plugins in paths $pluginPaths" }

                val pluginsFiles = pluginPaths.filter { it.exists() }
                    .flatMap { it.listFiles()!!.asIterable() }
                    .filter { it.isFile && it.extension.equals("zip", true) }
                    .map { it.canonicalFile }

                if (pluginsFiles.isNotEmpty()) {

                    logger.info { "Plugin jars found: ${pluginsFiles.count()}." }

                    pluginsFiles.forEach { pluginFile ->
                        logger.info { "Loading from $pluginFile." }

                        ZipFile(pluginFile).use { jar ->
                            val configPath = "plugin_config.json"
                            val configEntry = jar.getEntry(configPath)

                            if (configEntry != null) {
                                val configText = jar.getInputStream(configEntry).reader().readText()
                                val configJson = nonStrictJson.parseToJsonElement(configText) as JsonObject
                                val version = configJson["version"]?.jsonPrimitive?.contentOrNull ?: ""
                                val config = nonStrictJson.decodeFromJsonElement(
                                    PluginMetadata.serializer(),
                                    configJson
                                )
                                val pluginId = config.id

                                if (pluginId !in plugins.keys) {
                                    val agentFile = jar.extractPluginEntry(pluginId, "agent-part.jar")
                                    if (agentFile != null) {
                                        val dp = Plugin(
                                            pluginClass = Test2CodePlugin::class.java,
                                            agentPartFiles = AgentPartFiles(
                                                agentFile,
                                                jar.extractPluginEntry(pluginId, "native_plugin.dll"),
                                                jar.extractPluginEntry(pluginId, "libnative_plugin.so")
                                            ),
                                            pluginBean = config,
                                            version = version
                                        )
                                        plugins[pluginId] = dp
                                        logger.info { "Plugin '$pluginId' loaded, version '$version'" }

                                    }
                                } else {
                                    logger.warn { "Plugin $pluginId has already been loaded. Skipping loading from $pluginFile." }
                                }
                            } else {
                                logger.warn { "Error loading plugin from $pluginFile - no $configPath!" }
                            }
                        }
                    }
                } else {
                    logger.warn { "No plugins found!" }

                }
            } catch (ex: Exception) {
                logger.error(ex) { "Plugin loading was finished with exception" }
            }
        }

    }


    private fun ZipFile.extractPluginEntry(pluginId: String, entry: String): File? {
        val jarEntry: ZipEntry = getEntry(entry) ?: return null
        return getInputStream(jarEntry).use { istream ->
            val pluginDir = workDir.resolve("plugins").resolve(pluginId)
            pluginDir.mkdirs()
            File(pluginDir, jarEntry.name).apply {
                outputStream().use { ostream -> istream.copyTo(ostream) }
                deleteOnExit()
            }
        }
    }


}

private val nonStrictJson = Json { ignoreUnknownKeys = true }
