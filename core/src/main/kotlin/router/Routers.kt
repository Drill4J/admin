@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.epam.drill.admin.router

import de.nielsfalk.ktor.swagger.version.shared.*
import io.ktor.locations.*

const val agentGroup = "Agent operations"
const val systemGroup = "System operations"
const val agentPluginManagementGroup = "Agent's plugins operations"

object Routes {

    @Location("/api")
    class Api {
        @Group(agentGroup)
        @Location("/agents")
        class Agent {
            @Group(agentGroup)
            @Location("/{agentId}/toggle-standby")
            data class AgentToggleStandby(val agentId: String)

            @Group(agentPluginManagementGroup)
            @Location("/{agentId}/{pluginId}/unload-plugin")
            data class UnloadPlugin(val agentId: String, val pluginId: String)

            @Group(agentPluginManagementGroup)
            @Location("/{agentId}/{pluginId}/update-plugin")
            data class UpdatePlugin(val agentId: String, val pluginId: String)

            @Group(agentPluginManagementGroup)
            @Location("/{agentId}/{pluginId}/dispatch-action")
            data class DispatchPluginAction(val agentId: String, val pluginId: String)

            @Group(agentPluginManagementGroup)
            @Location("/{agentId}/{pluginId}/toggle-plugin")
            data class TogglePlugin(val agentId: String, val pluginId: String)

            @Group(agentPluginManagementGroup)
            @Location("/{agentId}/load-plugin")
            data class AddNewPlugin(val agentId: String)

            @Group(agentGroup)
            @Location("/{agentId}/register")
            data class RegisterAgent(val agentId: String)

            @Group(agentGroup)
            @Location("/{agentId}/unregister")
            data class UnregisterAgent(val agentId: String)

            @Group(systemGroup)
            @Location("/{agentId}/system-settings")
            data class SystemSettings(val agentId: String)

            @Group(agentPluginManagementGroup)
            @Location("/{agentId}/plugin/{pluginId}/data/{dataType}")
            data class PluginData(val agentId: String, val pluginId: String, val dataType: String)
        }

        @Group(systemGroup)
        @Location("/all/register")
        object RegisterAll

        @Location("/service-group/{serviceGroupId}")
        data class ServiceGroup(val serviceGroupId: String) {
            @Group(systemGroup)
            @Location("/register")
            data class Register(val serviceGroupParent: ServiceGroup)

            @Group(systemGroup)
            @Location("/plugin/{pluginId}")
            data class Plugin(val serviceGroupParent: ServiceGroup, val pluginId: String) {
                @Group(systemGroup)
                @Location("/dispatch-action")
                data class DispatchAction(val parent: Plugin)

                @Group(systemGroup)
                @Location("/data/{dataType}")
                data class Data(val parent: Plugin, val dataType: String)
            }
        }

        @Group(agentPluginManagementGroup)
        @Location("/{agentId}/{pluginId}/reset")
        data class ResetPlugin(val agentId: String, val pluginId: String)

        @Group(agentGroup)
        @Location("/{agentId}/reset")
        data class ResetAgent(val agentId: String)

        @Group(systemGroup)
        @Location("/reset")
        class ResetAllAgents

        @Group(agentGroup)
        @Location("/agent/{agentId}")
        data class UpdateAgentConfig(val agentId: String)

        @Group(systemGroup)
        @Location("/notifications/read")
        class ReadNotification()

        @Group(systemGroup)
        @Location("/notifications/delete")
        class DeleteNotification()

        @Group(systemGroup)
        @Location("/login")
        class Login
    }

}
