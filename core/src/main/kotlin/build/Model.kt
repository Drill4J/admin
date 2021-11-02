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
package com.epam.drill.admin.build

import com.epam.drill.common.*
import com.epam.dsm.*
import kotlinx.serialization.*

@Serializable
data class AgentBuild(
    @Id val id: AgentBuildId,
    val agentId: String,
    val info: BuildInfo,
    val detectedAt: Long = 0L,
)

@Serializable
data class AgentBuildId(
    val agentId: String,
    val version: String,
) : Comparable<AgentBuildId> {
    override fun compareTo(other: AgentBuildId): Int = agentId.compareTo(other.agentId).takeIf { it != 0 }
        ?: version.compareTo(other.version)
}

@Serializable
data class AgentBuildData(
    @Id val id: AgentBuildId,
    val agentId: String,
    val detectedAt: Long,
) {
    override fun equals(other: Any?) = other is AgentBuildData && id == other.id

    override fun hashCode() = id.hashCode()
}
