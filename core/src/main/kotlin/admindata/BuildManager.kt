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
package com.epam.drill.admin.admindata

import com.epam.drill.admin.build.*
import com.epam.drill.common.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*

class AgentBuildManager(
    val agentId: String,
    builds: Iterable<AgentBuild> = emptyList()
) {

    internal val agentBuilds get() = buildMap.values

    private val buildMap: PersistentMap<String, AgentBuild>
        get() = _buildMap.value

    private val _addedClasses = atomic(persistentListOf<ByteArray>())

    private val _buildMap = atomic(
        builds.associateBy { it.info.version }.toPersistentMap()
    )

    operator fun get(version: String) = buildMap[version]?.info

    internal fun delete(version: String) = _buildMap.update { it.remove(version) }

    internal fun init(version: String) = _buildMap.updateAndGet { map ->
        if (version !in map) {
            val build = AgentBuild(
                id = AgentBuildId(agentId, version),
                agentId = agentId,
                info = BuildInfo(
                    version = version
                ),
                detectedAt = System.currentTimeMillis()
            )
            map.put(version, build)
        } else map
    }.getValue(version)

    internal fun addClass(rawData: ByteArray) = _addedClasses.update { it + rawData }

    internal fun collectClasses(): List<ByteArray> = _addedClasses.getAndUpdate { persistentListOf() }
}
