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
import com.epam.drill.admin.store.*
import com.epam.drill.admin.util.trackTime
import com.epam.drill.plugin.api.*
import com.epam.dsm.*
import com.epam.dsm.find.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import mu.*

/**
 * Cache service for the agent data
 */
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

/**
 * Plugins part of the agent data
 *
 * @param agentId the agent ID
 * @param initialSettings the initial settings of the agent
 */
internal class AgentData(
    val agentId: String,
    initialSettings: SystemSettingsDto,
) : AdminData {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    val agentBuildManager get() = _agentBuildManager.value


    val settings: SystemSettingsDto get() = _settings.value

    private val _agentBuildManager = atomic(AgentBuildManager(agentId))

    private val _settings = atomic(initialSettings)

    //todo delete after removing of deprecated methods. EPMDJ-8608
    private val buildVersion = atomic("")

    /**
     * Load the build data from the database or store there if a build is new
     * @param version the build version
     * @return A sign of a new build or not
     * @features Agent attaching
     */
    suspend fun initBuild(version: String): Boolean {

        buildVersion.update { version }
        if (agentBuildManager.agentBuilds.none()) {
            loadStoredData()
        }
        return (agentBuildManager[version] == null).also {
            val agentBuild = agentBuildManager.init(version)
            store(agentBuild)
        }
    }


    /**
     * Update settings in the database if they have changed
     * @param settings the settings which need to update
     * @param block the function which will be called after updating
     * @features Agent registration
     */
    suspend fun updateSettings(
        settings: SystemSettingsDto,
    ) {
        val current = this.settings
        if (current != settings) {
            _settings.value = settings
            adminStore.store(toSummary())
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


    /**
     * Load builds and settings from DB and initialize agent data state
     * @features Agent attaching
     */
    private suspend fun loadStoredData() = adminStore.findById<AgentDataSummary>(agentId)?.let { summary ->
        logger.info { "Loading data for $agentId..." }
        _settings.value = summary.settings
        val builds: List<AgentBuild> = adminStore.findBy<AgentBuildData> {
            AgentBuildData::agentId eq agentId
        }.get().map { data ->
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
        _agentBuildManager.value = AgentBuildManager(
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

