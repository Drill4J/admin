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
@file:Suppress("unused")

package com.epam.drill.admin.api.routes

import de.nielsfalk.ktor.swagger.version.shared.*
import io.ktor.locations.*

@Location("/{prefix}")
class ApiRoot(val prefix: String = "api") {
    companion object {
        const val SYSTEM = "System operations"
        const val AGENT = "Agent Endpoints"
        const val AGENT_PLUGIN = "Agent Plugin Endpoints"
        const val GROUP = "Group Endpoints"
    }

    @Group(SYSTEM)
    @Location("/login")
    data class Login(val parent: ApiRoot)

    @Group(SYSTEM)
    @Location("/version")
    data class Version(val parent: ApiRoot)

    @Group(AGENT)
    @Location("/agents")
    data class Agents(val parent: ApiRoot) {

        @Group(AGENT)
        @Location("/{agentId}")
        data class Agent(val parent: Agents, val agentId: String)

        @Group(AGENT)
        @Location("/{agentId}/info")
        data class AgentInfo(val parent: Agents, val agentId: String)

        @Group(AGENT)
        @Location("/{agentId}/toggle")
        data class ToggleAgent(val parent: Agents, val agentId: String)

        @Group(AGENT)
        @Location("/{agentId}/logging")
        data class AgentLogging(val parent: Agents, val agentId: String)

        @Group(AGENT)
        @Location("/{agentId}/system-settings")
        data class SystemSettings(val parent: Agents, val agentId: String)

        @Group(AGENT_PLUGIN)
        @Location("/{agentId}/plugins")
        data class Plugins(val parent: Agents, val agentId: String)

        @Group(AGENT_PLUGIN)
        @Location("/{agentId}/plugins/{pluginId}")
        data class Plugin(val parent: Agents, val agentId: String, val pluginId: String)

        @Group(AGENT_PLUGIN)
        @Location("/{agentId}/plugins/{pluginId}/dispatch-action")
        data class DispatchPluginAction(val parent: Agents, val agentId: String, val pluginId: String)

        @Group(AGENT_PLUGIN)
        @Location("/{agentId}/plugins/{pluginId}/toggle")
        data class TogglePlugin(val parent: Agents, val agentId: String, val pluginId: String)

        @Group(AGENT_PLUGIN)
        @Location("/{agentId}/plugins/{pluginId}/data/{dataType}")
        data class PluginData(val parent: Agents, val agentId: String, val pluginId: String, val dataType: String)

        @Group(AGENT_PLUGIN)
        @Location("/{agentId}/plugins/{pluginId}/builds/summary")
        data class PluginBuildsSummary(val parent: Agents, val agentId: String, val pluginId: String)
    }

    @Group(GROUP)
    @Location("/groups/{groupId}")
    data class AgentGroup(val parent: ApiRoot, val groupId: String) {
        @Group(GROUP)
        @Location("/system-settings")
        data class SystemSettings(val parent: AgentGroup)

        @Group(GROUP)
        @Location("/plugins")
        data class Plugins(val parent: AgentGroup)

        @Group(GROUP)
        @Location("/plugins/{pluginId}")
        data class Plugin(val parent: AgentGroup, val pluginId: String) {
            @Group(GROUP)
            @Location("/dispatch-action")
            data class DispatchAction(val parent: Plugin)

            @Group(GROUP)
            @Location("/data/{dataType}")
            data class Data(val parent: Plugin, val dataType: String)
        }
    }
}
