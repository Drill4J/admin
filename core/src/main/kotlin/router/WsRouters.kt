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
package com.epam.drill.admin.router

import io.ktor.locations.*

/**
 * WebSocket routes for Admin UI
 */
//TODO Remove and refactor in EPMDJ-9842
object WsRoutes {
    @Location("/groups/{groupId}")
    data class Group(val groupId: String)

    @Location("/groups/{groupId}/plugins")
    data class GroupPlugins(val groupId: String)

    @Location("/agents")
    class Agents

    @Location("/agents/{agentId}/plugins")
    data class AgentPlugins(val agentId: String)

    @Location("/agents/{agentId}")
    data class Agent(val agentId: String)

    @Location("/plugins")
    class Plugins

    @Location("/agents/{agentId}/builds/summary")
    data class AgentBuildsSummary(val agentId: String)

    @Location("/version")
    object WsVersion
}
