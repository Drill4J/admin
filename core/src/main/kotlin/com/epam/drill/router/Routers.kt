@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.epam.drill.router

import io.ktor.locations.*

object Routes {

    @Location("/api")
    class Api {
        @Location("/agents")
        class Agent {
            @Location("/{agentId}/toggle-standby")
            data class AgentToggleStandby(val agentId: String)

            @Location("/{agentId}/{pluginId}/unload-plugin")
            data class UnloadPlugin(val agentId: String, val pluginId: String)

            @Location("/{agentId}/{pluginId}/update-plugin")
            data class UpdatePlugin(val agentId: String, val pluginId: String)

            @Location("/{agentId}/{pluginId}/dispatch-action")
            data class DispatchPluginAction(val agentId: String, val pluginId: String)

            @Location("/{agentId}/{pluginId}/toggle-plugin")
            data class TogglePlugin(val agentId: String, val pluginId: String)

            @Location("/{agentId}/load-plugin")
            data class AddNewPlugin(val agentId: String)

            @Location("/{agentId}/register")
            data class RegisterAgent(val agentId: String)

            @Location("/{agentId}/activate")
            data class ActivateAgents(val agentId: String)

            @Location("/{agentId}/unregister")
            data class UnregisterAgent(val agentId: String)

            @Location("/{agentId}/system-settings")
            data class SystemSettings(val agentId: String)

            @Location("/{agentId}/rename-build")
            data class RenameBuildVersion(val agentId: String)

            @Location("/{agentId}/{pluginId}/get-data")
            data class GetPluginData(val agentId: String, val pluginId: String)
        }


        @Location("/all/register")
        object RegisterAll

        @Location("/all/{pluginId}/dispatch-action")
        data class DispatchAllPluginAction(val pluginId: String)

        @Location("/service-group")
        class ServiceGroup {
            @Location("/{serviceGroupId}/register")
            data class Register(val serviceGroupId: String)

            @Location("/{serviceGroupId}/{pluginId}/dispatch-action")
            data class DispatchPluginAction(val serviceGroupId: String, val pluginId: String)


        }

        @Location("/{agentId}/{pluginId}/reset")
        data class ResetPlugin(val agentId: String, val pluginId: String)

        @Location("/{agentId}/reset")
        data class ResetAgent(val agentId: String)

        @Location("/reset")
        class ResetAllAgents

        @Location("/agent/{agentId}")
        data class UpdateAgentConfig(val agentId: String)

        @Location("/notifications/read")
        class ReadNotification()

        @Location("/notifications/delete")
        class DeleteNotification()

        @Location("/login")
        class Login
    }

}