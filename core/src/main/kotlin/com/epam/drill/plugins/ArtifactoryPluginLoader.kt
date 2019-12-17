package com.epam.drill.plugins

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import mu.*
import java.io.*

private val logger = KotlinLogging.logger {}

data class ArtifactoryPluginLoader(
    val baseUrl: String,
    val basePath: String,
    val repo: String,
    val storageDir: File,
    val devMode: Boolean = true //TODO dev mode handling
) {
    suspend fun loadPlugins(pluginIds: Iterable<String>) {
        logger.info { "Loading plugins $pluginIds from artifactory..." }
        HttpClient(CIO).use { client ->
            for (pluginId in pluginIds) {
                client.load(pluginId)
            }
        }
        logger.info { "Plugins loaded." }
    }

    suspend fun HttpClient.load(pluginId: String) {
        val version: String = getVersion(pluginId)
        logger.info { "Loading $pluginId v$version" }
        val targetFilename = "$pluginId-plugin-$version.zip"
        val artifactPath = "com/epam/drill/$pluginId-plugin/$version/$targetFilename"
        val targetFile = storageDir.resolve(targetFilename)
        if (devMode) {
            val localMavenRepo = File(System.getProperty("user.home"), ".m2").resolve("repository")
            val artifactFile = localMavenRepo.resolve(artifactPath)
            if (artifactFile.exists()) {
                logger.info { "Copying $artifactFile from local maven repo" }
                artifactFile.copyTo(targetFile, overwrite = true)
            } else {
                downloadPlugin(artifactPath, targetFile)
            }
        } else {
            downloadPlugin(artifactPath, targetFile)
        }
    }

    private suspend fun HttpClient.getVersion(pluginId: String): String {
        return when (val envVersion = System.getenv("T2CM_VERSION")?.trim() ?: "") {
            "", "latest" -> get(baseUrl) {
                url.path(basePath, "api/search/latestVersion")
                url.parameters.apply {
                    append("g", "com.epam.drill")
                    append("a", "$pluginId-plugin")
                    append("v", " ")
                    append("repos", repo)
                }
            }
            else -> envVersion
        }
    }

    private suspend fun HttpClient.downloadPlugin(artifactPath: String, targetFile: File) {
        logger.info { "Downloading $artifactPath" }
        val pluginBytes: ByteArray = get(baseUrl) {
            url.path(basePath, repo, artifactPath)
        }
        targetFile.writeBytes(pluginBytes)
    }
}
