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

import com.epam.drill.admin.api.agent.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.admin.plugins.*

internal fun Iterable<AgentInfo>.mapToDto(
    agentManager: AgentManager,
): List<AgentInfoDto> = map { it.toDto(agentManager) }

internal fun AgentManager.all(): List<AgentInfoDto> = agentStorage.values.map { entry ->
    entry.info.toDto(this)
}

//TODO remove after EPMDJ-8292
internal fun AgentManager.agentsActiveBuild(
    buildManager: BuildManager,
) = agentStorage.values.map { agent ->
    val info = agent.info
    AgentBuildDto(info.id, info.groupId, info.agentStatus, info.agentType.notation, info.toAgentBuildDto(buildManager))
}

internal fun Plugins.ofAgent(info: AgentInfo) = info.plugins.mapNotNull { this[it] }

internal fun AgentCreationDto.toAgentInfo(installedPlugins: Plugins) = AgentInfo(
    id = id,
    agentType = agentType,
    name = name,
    agentStatus = AgentStatus.PREREGISTERED,
    groupId = group,
    environment = environment,
    description = description,
    build = AgentBuildInfo(version = ""),
    plugins = plugins.filterTo(mutableSetOf()) { it in installedPlugins }
)

internal fun CommonAgentConfig.toAgentInfo() = AgentInfo(
    id = id,
    name = id,
    agentStatus = AgentStatus.NOT_REGISTERED,
    groupId = serviceGroupId,
    environment = "",
    description = "",
    build = AgentBuildInfo(
        version = buildVersion,
        agentVersion = agentVersion,
    ),
    agentType = AgentType.valueOf(agentType.name)
)

internal fun AgentInfo.toDto(
    agentManager: AgentManager,
): AgentInfoDto = run {
    val plugins = agentManager.plugins.ofAgent(this)
    AgentInfoDto(
        id = id,
        group = groupId,
        name = name,
        description = description,
        environment = environment,
        agentStatus = agentStatus,
        adminUrl = adminUrl,
        activePluginsCount = plugins.count(),
        agentType = agentType.notation,
        plugins = plugins.mapToDto().toSet(),
    )
}

internal fun AgentInfo.toAgentBuildDto(
    buildManager: BuildManager,
): AgentBuildInfoDto = build.run {
    AgentBuildInfoDto(
        buildVersion = version,
        buildStatus = buildManager.buildStatus(id),
        ipAddress = ipAddress,
        agentVersion = agentVersion,
        systemSettings = buildManager.buildData(id).settings,
        instanceIds = buildManager.instanceIds(id).keys
    )
}


internal fun AgentInfo.toCommonInfo() = CommonAgentInfo(
    id = id,
    agentType = agentType.name,
    agentVersion = build.agentVersion,
    serviceGroup = groupId,
    buildVersion = build.version,
    name = name,
    environment = environment,
    description = description
)

internal fun AgentInfo.toAgentBuildKey() = AgentBuildKey(id, build.version)

internal suspend fun Iterable<AgentWsSession>.applyEach(block: suspend AgentWsSession.() -> Unit) = forEach {
    block(it)
}

internal fun AgentInfo.debugString(
    instanceId: String,
) = "Agent(id=$id, buildVersion=${build.version}, instanceId=$instanceId)"
