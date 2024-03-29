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

import com.epam.drill.admin.api.agent.AgentCreationDto
import com.epam.drill.admin.api.agent.AgentStatus
import com.epam.drill.admin.api.agent.AgentType
import com.epam.drill.admin.api.agent.SystemSettingsDto
import com.epam.dsm.Id
import kotlinx.serialization.Serializable

typealias CommonAgentConfig = com.epam.drill.common.agent.configuration.AgentConfig
typealias CommonAgentInfo = com.epam.drill.common.AgentInfo
typealias PackagesPrefixes = com.epam.drill.common.agent.configuration.PackagesPrefixes
typealias BuildInfo = com.epam.drill.admin.build.BuildInfo

@Serializable
data class AgentInfo(
    @Id val id: String,
    val name: String,
    val groupId: String = "",
    val agentStatus: AgentStatus, // TODO deprecate (with change to universal backend API)
    val environment: String = "", // TODO deprecate (with change to universal backend API)
    val description: String, // TODO deprecate (with change to universal backend API)
    val agentType: AgentType,  // TODO consider deprecation (with change to universal backend API)
    val adminUrl: String = "", // TODO deprecate (with change to universal backend API)
    val build: AgentBuildInfo,
    val plugins: Set<String> = emptySet(), // TODO deprecate (with change to universal backend API)
) {
    override fun equals(other: Any?): Boolean = other is AgentInfo && id == other.id

    override fun hashCode(): Int = id.hashCode()
}

@Serializable
data class AgentBuildInfo(
    val version: String,
    val agentVersion: String = "",
    val ipAddress: String = "",
)

/**
 * Link between the agent and the build
 * @param agentId Agent ID
 * @param buildVersion Application build version
 */
@Serializable
data class AgentBuildKey(
    val agentId: String,
    val buildVersion: String,
)

@Serializable
internal class PreparedAgentData(
    @Id val id: String,
    val dto: AgentCreationDto,
)

@Serializable
internal data class AgentDataSummary(
    @Id val agentId: String,
    val settings: SystemSettingsDto,
)


@Serializable
internal class AgentMetadata(
    @Id val id: AgentBuildKey,
)
