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
internal fun AgentInfo.getStatus(): AgentStatus = instances.takeIf { instances ->
    instances.any { it.value.status != AgentStatus.OFFLINE }
}?.let { instances ->
    takeIf { it.isRegistered }?.let {
        AgentStatus.ONLINE.takeIf { instances.any { it.value.status == AgentStatus.ONLINE } }
            ?: AgentStatus.BUSY
    } ?: AgentStatus.NOT_REGISTERED
} ?: AgentStatus.OFFLINE

internal fun Plugins.ofAgent(info: AgentInfo) = info.plugins.mapNotNull { this[it] }

internal fun AgentCreationDto.toAgentInfo(installedPlugins: Plugins) = AgentInfo(
    id = id,
    agentType = agentType,
    name = name,
    isRegistered = true,
    groupId = group,
    environment = environment,
    description = description,
    agentVersion = "",
    buildVersion = "",
    plugins = plugins.filterTo(mutableSetOf()) { it in installedPlugins }
)

internal fun CommonAgentConfig.toAgentInfo() = AgentInfo(
    id = id,
    name = id,
    isRegistered = false,
    groupId = serviceGroupId,
    environment = "",
    description = "",
    agentVersion = agentVersion,
    buildVersion = buildVersion,
    agentType = AgentType.valueOf(agentType.name)
)

internal fun AgentInfo.toDto(
    agentManager: AgentManager,
): AgentInfoDto = run {
    val plugins = agentManager.plugins.ofAgent(this)
    val instanceIds = agentManager.instanceIds(id).keys
    AgentInfoDto(
        id = id,
        group = groupId,
        instanceIds = instanceIds,
        name = name,
        description = description,
        environment = environment,
        status = getStatus(),
        buildVersion = buildVersion,
        adminUrl = adminUrl,
        ipAddress = ipAddress,
        activePluginsCount = plugins.activePluginsCount(),
        agentType = agentType.notation,
        agentVersion = agentVersion,
        systemSettings = agentManager.adminData(id).settings,
        plugins = plugins.mapToDto().toSet()
    )
}

internal fun AgentInfo.toCommonInfo() = CommonAgentInfo(
    id = id,
    agentType = agentType.name,
    agentVersion = agentVersion,
    serviceGroup = groupId,
    buildVersion = buildVersion,
    name = name,
    environment = environment,
    description = description
)

internal suspend fun Iterable<AgentWsSession>.applyEach(block: suspend AgentWsSession.() -> Unit) = forEach {
    block(it)
}

private fun Iterable<Plugin>.activePluginsCount(): Int = count { it.pluginBean.enabled }

internal fun AgentInfo.debugString(
    instanceId: String
) = "Agent(id=$id, buildVersion=$buildVersion, instanceId=$instanceId)"
