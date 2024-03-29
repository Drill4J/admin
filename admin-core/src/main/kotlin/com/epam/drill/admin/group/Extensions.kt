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
import com.epam.drill.admin.api.group.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.plugins.*

internal fun GroupedAgents.toDto(agentManager: AgentManager) = GroupedAgentsDto(
    single = first.agentInfos.mapToDto(agentManager),
    grouped = second.map { it.toDto(agentManager) }
)

internal fun AgentGroup.toDto(
    agentManager: AgentManager,
) = JoinedGroupDto(
    group = group,
    agents = agentInfos.mapToDto(agentManager),
    plugins = agentManager.plugins.values.mapToDto(agentInfos)
)

internal fun StoredGroup.toDto() = GroupDto(
    id = id,
    name = name,
    description = description,
    environment = environment,
    systemSettings = systemSettings
)

internal fun GroupDto.toModel() = StoredGroup(
    id = id,
    name = name,
    description = description,
    environment = environment,
    systemSettings = systemSettings
)
