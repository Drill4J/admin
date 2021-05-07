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
package com.epam.drill.admin.group

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.api.agent.*
import com.epam.drill.admin.api.group.*
import com.epam.kodux.*
import kotlinx.serialization.*

@Serializable
internal data class Group(
    @Id val id: String,
    val name: String,
    val description: String = "",
    val environment: String = "",
    val systemSettings: SystemSettingsDto = SystemSettingsDto(),
)

internal typealias GroupedAgents = Pair<SingleAgents, List<AgentGroup>>

internal sealed class AgentList

internal class SingleAgents(val agentInfos: List<AgentInfo>) : AgentList()

internal class AgentGroup(val group: GroupDto, val agentInfos: List<AgentInfo>) : AgentList()
