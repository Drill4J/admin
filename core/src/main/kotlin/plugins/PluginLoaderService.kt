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
import org.kodein.di.*
import org.kodein.di.generic.*
import java.io.*
import java.lang.System.*
import java.util.jar.*
import java.util.zip.*

private val logger = KotlinLogging.logger {}

class PluginLoaderService(
    override val kodein: Kodein,
    val workDir: File = File(getenv("DRILL_HOME"), "work")
) : KodeinAware {
    private val plugins: Plugins by instance()
    private val application: Application by instance()

    private val pluginStoragePath = File("distr").resolve("adminStorage")
    private val pluginPaths: List<File> = listOf(pluginStoragePath).map { it.canonicalFile }

    private val allowedPlugins = setOf("test2code")

    init {
        runBlocking(Dispatchers.Default) {
            pluginStoragePath.mkdirs()
            val remoteEnabled = application.drillConfig
                .config("plugins")
                .config("remote")
                .property("enabled").getString().toBoolean()
            if (remoteEnabled) {
                val pluginLoader = ArtifactoryPluginLoader(
                    baseUrl = "https://oss.jfrog.org",
                    basePath = "artifactory",
                    repo = "oss-release-local",
                    storageDir = pluginStoragePath,
                    devMode = application.isDevMode
                )
                pluginLoader.loadPlugins(allowedPlugins)
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
                                @Suppress("EXPERIMENTAL_API_USAGE")
                                val config = Json.parse(PluginMetadata.serializer(), configText)
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
                                                adminPartClass,
                                                AgentPartFiles(
                                                    agentFile,
                                                    jar.extractPluginEntry(pluginId, "native_plugin.dll"),
                                                    jar.extractPluginEntry(pluginId, "libnative_plugin.so")
                                                ),
                                                config
                                            )
                                            plugins[pluginId] = dp
                                            logger.info { "Plugin '$pluginId' was loaded successfully." }
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
        adminPartFile: File, adminJar: JarFile
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
