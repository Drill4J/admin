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
package com.epam.drill.admin.group

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.api.agent.*
import com.epam.drill.admin.api.group.*
import com.epam.drill.admin.config.*
import com.epam.drill.admin.store.*
import com.epam.dsm.*
import io.ktor.application.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import mu.*
import org.kodein.di.*


internal class GroupManager(override val di: DI) : DIAware {
    private val logger = KotlinLogging.logger {}

    private val app by instance<Application>()

    private val _state = atomic(persistentHashMapOf<String, GroupDto>())

    init {
        runBlocking {
            val groups = adminStore.getAll<StoredGroup>()
            _state.update {
                it.mutate { map ->
                    for (group in groups) {
                        map[group.id] = group.toDto()
                    }
                }
            }
        }
    }

    fun all(): Collection<GroupDto> = _state.value.values

    operator fun get(groupId: String): GroupDto? = _state.value[groupId]

    suspend fun syncOnAttach(groupId: String) {
        val oldGroups = _state.getAndUpdate { groups ->
            groups.takeIf { groupId in it } ?: groups.put(
                key = groupId,
                value = GroupDto(
                    id = groupId,
                    name = groupId,
                    systemSettings = SystemSettingsDto(
                        packages = app.drillDefaultPackages
                    )
                )
            )
        }
        val groups = _state.value
        if (groups !== oldGroups) {
            groups[groupId]?.let { adminStore.storeGroup(it.toModel()) }
        }
    }

    suspend fun update(group: GroupDto): GroupDto? = group.id.let { id ->
        val oldGroups = _state.getAndUpdate { groups ->
            groups[id]?.takeIf { it != group }?.let { groups.put(id, group) } ?: groups
        }
        val groups = _state.value
        groups[id]?.also {
            if (oldGroups !== groups) {
                oldGroups[id]?.also { adminStore.store(it, group) }
            }
        }
    }

    fun group(agents: Iterable<AgentInfo>): GroupedAgents {
        val groups = _state.value
        val agentGroups = agents.filter { it.groupId.isEmpty() || it.groupId in groups }
            .groupBy { it.groupId }
        val singleAgents = SingleAgents(agentGroups[""] ?: emptyList())
        val groupedAgents = agentGroups.filterKeys(String::isNotEmpty)
            .map { (key, value) -> AgentGroup(groups[key]!!, value) }
        return singleAgents to groupedAgents
    }

    suspend fun updateSystemSettings(
        group: GroupDto,
        systemSettings: SystemSettingsDto,
    ): GroupDto? = update(
        group.copy(
            systemSettings = SystemSettingsDto(
                packages = systemSettings.packages,
                sessionIdHeaderName = systemSettings.sessionIdHeaderName,
                targetHost = systemSettings.targetHost
            )
        )
    )

    private suspend fun StoreClient.store(
        oldValue: GroupDto,
        groupDto: GroupDto,
    ): GroupDto = run {
        logger.debug { "Updating group ${groupDto.id}, old: $oldValue new: $groupDto" }
        storeGroup(groupDto.toModel())
    }.toDto()
}

private suspend fun StoreClient.storeGroup(
    group: StoredGroup,
): StoredGroup = this.store(group)
