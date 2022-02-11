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
package com.epam.drill.admin.agent

import com.epam.drill.admin.admindata.*
import com.epam.drill.admin.api.agent.*
import com.epam.drill.admin.build.*
import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.store.*
import com.epam.drill.admin.util.*
import com.epam.drill.admin.util.trackTime
import com.epam.drill.plugin.api.*
import com.epam.dsm.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.protobuf.*
import mu.*

internal class AgentDataCache {

    private val _data = atomic(persistentMapOf<String, AgentData>())

    operator fun get(key: String): AgentData? = _data.value[key]

    operator fun set(key: String, value: AgentData) {
        _data.update {
            it.put(key, value)
        }
    }

    fun getOrPut(
        key: String,
        provider: () -> AgentData,
    ): AgentData = _data.updateAndGet {
        if (key !in it) {
            it.put(key, provider())
        } else it
    }[key]!!
}

internal class AgentData(
    val agentId: String,
    initialSettings: SystemSettingsDto,
) : AdminData {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    val buildManager get() = _buildManager.value

    override suspend fun loadClassBytes(): Map<String, ByteArray> =
        adminStore.loadClasses(AgentKey(agentId, buildVersion.value))

    override suspend fun loadClassBytes(buildVersion: String) = adminStore.loadClasses(AgentKey(agentId, buildVersion))

    val settings: SystemSettingsDto get() = _settings.value

    private val _buildManager = atomic(AgentBuildManager(agentId))

    private val _settings = atomic(initialSettings)

    //todo delete after removing of deprecated methods. EPMDJ-8608
    private val buildVersion = atomic("")

    suspend fun initBuild(version: String): Boolean {

        buildVersion.update { version }
        if (buildManager.agentBuilds.none()) {
            loadStoredData()
        }
        return (buildManager[version] == null).also {
            val agentBuild = buildManager.init(version)
            store(agentBuild)
        }
    }

    internal suspend fun initClasses(buildVersion: String) {
        val classBytes: List<ByteArray> = buildManager.collectClasses()
        val classBytesSize = classBytes.sumOf { it.size } / 1024
        val agentKey = AgentKey(agentId, buildVersion)
        logger.debug { "Saving ${classBytes.size} classes with $classBytesSize KB for $agentKey..." }
        trackTime("initClasses") {
            adminStore.storeClasses(agentKey, classBytes)
            adminStore.storeMetadata(agentKey, Metadata(classBytes.size, classBytesSize))
        }
    }

    suspend fun updateSettings(
        settings: SystemSettingsDto,
        block: suspend (SystemSettingsDto) -> Unit = {},
    ) {
        val current = this.settings
        if (current != settings) {
            _settings.value = settings
            adminStore.store(toSummary())
            block(current)
        }
    }

    suspend fun store(agentBuild: AgentBuild) = agentBuild.run {
        logger.info { "Saving build ${agentBuild.id}..." }
        val buildData = AgentBuildData(
            id = id,
            agentId = id.agentId,
            detectedAt = detectedAt
        )
        trackTime("storeBuild") {
            adminStore.executeInAsyncTransaction {
                store(buildData)
                store(toSummary())
            }
        }

        logger.debug { "Saved build ${agentBuild.id}." }
    }

    suspend fun deleteClassBytes(agentKey: AgentKey) = adminStore.deleteClasses(agentKey)

    private suspend fun loadStoredData() = adminStore.findById<AgentDataSummary>(agentId)?.let { summary ->
        logger.info { "Loading data for $agentId..." }
        _settings.value = summary.settings
        val builds: List<AgentBuild> = adminStore.findBy<AgentBuildData> {
            AgentBuildData::agentId eq agentId
        }.map { data ->
            data.run {
                AgentBuild(
                    id = id,
                    agentId = agentId,
                    detectedAt = detectedAt,
                    info = BuildInfo(
                        version = id.version
                    )
                )
            }
        }
        _buildManager.value = AgentBuildManager(
            agentId = agentId,
            builds = builds
        )
        logger.debug { "Loaded data for $agentId" }
    }

    private fun toSummary() = AgentDataSummary(
        agentId = agentId,
        settings = settings
    )
}

private suspend fun StoreClient.storeClasses(
    agentKey: AgentKey,
    classBytes: List<ByteArray>,
) {
    trackTime("storeClasses") {
        logger.debug { "Storing for $agentKey class bytes ${classBytes.size}..." }
        val storedData = StoredCodeData(
            id = agentKey,
            data = classBytes
        )
        store(storedData)
    }
}

private suspend fun StoreClient.loadClasses(
    agentKey: AgentKey,
): Map<String, ByteArray> = trackTime("loadClasses") {
    findById<StoredCodeData>(agentKey)?.run {
        data.asSequence().map {
            ProtoBuf.load(ByteClass.serializer(), it)
        }.associate { it.className to it.bytes }
    } ?: let {
        logger.warn { "Can not find classBytes for $agentKey" }
        emptyMap()
    }
}

private suspend fun StoreClient.deleteClasses(
    agentKey: AgentKey,
) = trackTime("deleteClasses") {
    logger.debug { "Deleting class bytes for $agentKey..." }
    deleteById<StoredCodeData>(agentKey)
}
