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
        logger.info { "Loading plugins $pluginIds from artifactory '$baseUrl'..." }
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
        val artifactPath = "com/epam/drill/plugins/$pluginId-plugin/$version/$targetFilename"
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

    private suspend fun HttpClient.getVersion(pluginId: String): String = run {
        when (val envVersion = pluginId.toEnvVersion()) {
            "", "latest" -> get(baseUrl) {
                url.path(basePath, "api/search/latestVersion")
                url.parameters.apply {
                    append("g", PLUGIN_PACKAGE)
                    append("a", "$pluginId-plugin")
                    append("v", " ")
                    append("repos", repo)
                }
            }
            else -> envVersion
        }
    }

    private fun String.toEnvVersion(): String  = run {
        val normalizedId = replace(Regex("\\s|-"), "_").toUpperCase()
        System.getenv("${normalizedId}_PLUGIN_VERSION") ?: ""
    }

    private suspend fun HttpClient.downloadPlugin(artifactPath: String, targetFile: File) {
        logger.info { "Downloading $artifactPath" }
        val pluginBytes: ByteArray = get(baseUrl) {
            url.path(basePath, repo, artifactPath)
        }
        targetFile.writeBytes(pluginBytes)
    }
}
