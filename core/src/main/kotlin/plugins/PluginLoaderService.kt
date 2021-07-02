/**
 * Copyright 2020 EPAM Systems
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


import com.epam.drill.*
import com.epam.drill.admin.config.*
import com.epam.drill.admin.plugin.*
import com.epam.drill.common.*
import com.epam.drill.plugin.api.end.*
import io.ktor.application.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import mu.*
import java.io.*
import java.lang.System.*
import java.util.jar.*
import java.util.zip.*

private val logger = KotlinLogging.logger {}

const val TEST2CODE = "test2code"
val defaultPlugins = setOf(TEST2CODE)

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
            val remoteEnabled = application.drillConfig
                .propertyOrNull("plugins.remote.enabled")?.getString()?.toBoolean() ?: false

            val artifactoryName = application.drillConfig
                .propertyOrNull("plugins.artifactory.name")?.getString() ?: ""

            val allowedPlugins = application.drillConfig
                .propertyOrNull("plugin.ids")?.getString()?.split(",")?.toSet() ?: defaultPlugins

            if (remoteEnabled) {
                val artifactory = runCatching {
                    Artifactory.valueOf(artifactoryName)
                }.onFailure {
                    logger.warn {
                        "Please make sure the artifactory name is correct:${
                            Artifactory.values().joinToString()
                        }"
                    }
                }.getOrNull() ?: Artifactory.GITHUB
                val pluginLoaderJfrog = ArtifactoryPluginLoader(
                    artifactory = artifactory,
                    storageDir = pluginStoragePath,
                    devMode = application.isDevMode
                )
                pluginLoaderJfrog.loadPlugins(allowedPlugins)
            }

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
                                    val adminPartFile = jar.extractPluginEntry(pluginId, "admin-part.jar")
                                    val agentFile = jar.extractPluginEntry(pluginId, "agent-part.jar")

                                    if (adminPartFile != null && agentFile != null) {
                                        val adminPartClass = JarFile(adminPartFile).use { adminJar ->
                                            processAdminPart(adminPartFile, adminJar)
                                        }

                                        if (adminPartClass != null) {
                                            val dp = Plugin(
                                                pluginClass = adminPartClass,
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
                                        } else {
                                            logger.warn { "Admin Plugin API class was not found for $pluginId" }
                                        }
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

    private fun processAdminPart(
        adminPartFile: File, adminJar: JarFile,
    ): Class<AdminPluginPart<*>>? {
        val pluginClassLoader = PluginClassLoader(adminPartFile.toURI().toURL())
        val entrySet = adminJar.entries().iterator().asSequence().toSet()
        val pluginApiClass =
            retrieveApiClass(AdminPluginPart::class.java, entrySet, pluginClassLoader)
        @Suppress("UNCHECKED_CAST")
        return pluginApiClass as? Class<AdminPluginPart<*>>
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
