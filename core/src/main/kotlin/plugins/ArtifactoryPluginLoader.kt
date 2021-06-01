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
import kotlinx.serialization.json.*
import mu.*
import java.io.*

private val logger = KotlinLogging.logger {}

data class ArtifactoryPluginLoader(
    val artifactory: Artifactory,
    val storageDir: File,
    val devMode: Boolean = true //TODO dev mode handling
) {
    suspend fun loadPlugins(pluginIds: Iterable<String>) {
        HttpClient(CIO).use { client ->
            for (pluginId in pluginIds) {
                val url = pluginId.downloadUrl()
                if (url.isNotBlank()) {
                    client.downloadPlugin(url)
                } else {
                    logger.info { "Loading plugin $pluginId from artifactory '${artifactory.baseUrl}' ..." }
                    client.load(pluginId)
                }
            }
        }
        logger.info { "Plugins loaded." }
    }

    suspend fun HttpClient.load(pluginId: String) {
        val version: String = getVersion(pluginId)
        logger.info { "Loading $pluginId v$version" }
        val targetFilename = "$pluginId-plugin-$version.zip"
        val targetFile = storageDir.resolve(targetFilename)
        if (devMode) {
            val artifactPath = "com/epam/drill/plugins/$pluginId-plugin/$version/$targetFilename"
            val localMavenRepo = File(System.getProperty("user.home"), ".m2").resolve("repository")
            val artifactFile = localMavenRepo.resolve(artifactPath)
            if (artifactFile.exists()) {
                logger.info { "Copying $artifactFile from local maven repo" }
                artifactFile.copyTo(targetFile, overwrite = true)
            } else {
                downloadPlugin(pluginId, version, targetFile)
            }
        } else {
            downloadPlugin(pluginId, version, targetFile)
        }
    }

    private suspend fun HttpClient.getVersion(pluginId: String): String = pluginId.toEnvVersion().let { envVersion ->
        if (envVersion in listOf("", "latest")) {
            when (artifactory) {
                Artifactory.GITHUB -> run {
                    val releases = get<String>(artifactory.baseUrl) {
                        url.path(artifactory.basePath, artifactory.repo, "$pluginId-plugin", "releases")
                        url.parameters.append("prerelease", "true")
                    }.let { Json.parseToJsonElement(it) }
                    releases.jsonArray.first().jsonObject["tag_name"]?.jsonPrimitive?.content?.replace("v", "") ?: ""
                }
                else -> run {
                    get(artifactory.baseUrl) {
                        url.path(artifactory.basePath, "api/search/latestVersion")
                        url.parameters.apply {
                            append("g", PLUGIN_PACKAGE)
                            append("a", "$pluginId-plugin")
                            append("v", " ")
                            append("repos", artifactory.repo)
                        }
                    }
                }
            }
        } else envVersion
    }

    private fun String.toEnvVersion(): String = run {
        val normalizedId = replace(Regex("\\s|-"), "_").toUpperCase()
        System.getenv("${normalizedId}_PLUGIN_VERSION") ?: ""
    }

    private fun String.downloadUrl(): String = run {
        val normalizedId = replace(Regex("\\s|-"), "_").toUpperCase()
        System.getenv("${normalizedId}_PLUGIN_URL") ?: ""
    }

    private suspend fun HttpClient.downloadPlugin(pluginId: String, version: String, targetFile: File) {
        val pluginBytes: ByteArray = when (artifactory) {
            Artifactory.GITHUB -> run {
                val artifactPath = "$pluginId-plugin/releases/download/v$version/${targetFile.name}"
                logger.info { "Downloading $artifactPath" }
                get(artifactory.baseUrl.replace("api.", "")) {
                    url.path(artifactory.repo, artifactPath)
                }
            }
            else -> run {
                val artifactPath = "com/epam/drill/plugins/$pluginId-plugin/$version/${targetFile.name}"
                logger.info { "Downloading $artifactPath" }
                get(artifactory.baseUrl) {
                    url.path(artifactory.basePath, artifactory.repo, artifactPath)
                }
            }
        }
        targetFile.writeBytes(pluginBytes)
    }

    private suspend fun HttpClient.downloadPlugin(url: String) {
        val targetFilename = url.split("/").last()
        logger.info { "Downloading $url to $targetFilename" }
        val targetFile = storageDir.resolve(targetFilename)
        targetFile.writeBytes(get(url))
    }
}

enum class Artifactory(val baseUrl: String, val basePath: String, val repo: String) {
    GITHUB("https://api.github.com/", "repos", "Drill4J"),
    OSS_JFROG("https://oss.jfrog.org", "artifactory", "oss-release-local"),
    DRILL4J_JFROG("https://drill4j.jfrog.io", "artifactory", "drill")
}
