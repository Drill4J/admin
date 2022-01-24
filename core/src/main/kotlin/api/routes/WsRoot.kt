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
package com.epam.drill.admin.api.routes

import io.ktor.locations.*

object WsRoot {
    @Location("/api/version")
    object Version

    @Location("/api/agents")
    class Agents

    //TODO remove after EPMDJ-8323
    @Location("/api/agents/build")
    class AgentsActiveBuild

    @Location("/api/agent/{agentId}")
    data class Agent(val agentId: String)

    //TODO EPMDJ-9812 nested structure doesn't work
    @Location("/api/agent/{agentId}/build/{buildVersion}")
    data class AgentBuild(val agentId: String, val buildVersion: String)

    @Location("/api/agent/{agentId}/builds")
    data class AgentBuilds(val agentId: String)

    @Location("/api/groups")
    class Groups

    @Location("/api/groups/{groupId}")
    data class Group(val groupId: String)
}
