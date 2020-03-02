@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.epam.drill.admin.router

import de.nielsfalk.ktor.swagger.version.shared.*
import io.ktor.locations.*

private const val SYSTEM = "System operations"
private const val AGENT = "Agent Endpoints"
private const val AGENT_PLUGIN = "Agent Plugin Endpoints"
private const val SERVICE_GROUP = "Service Group Endpoints"

object Routes {

    @Location("/api")
    object Api {
        @Group(SYSTEM)
        @Location("/login")
        class Login

        @Group(AGENT)
        @Location("/agents")
        object Agents {
            @Group(SYSTEM)
            @Location("/reset")
            object Reset

            @Group(AGENT)
            @Location("/{agentId}")
            data class Agent(val agentId: String)

            @Group(AGENT)
            @Location("/{agentId}/toggle")
            data class ToggleAgent(val agentId: String)

            @Group(AGENT)
            @Location("/{agentId}/reset")
            data class ResetAgent(val agentId: String)

            @Group(AGENT)
            @Location("/{agentId}/logging-levels")
            data class LoggingLevels(val agentId: String)

            @Group(AGENT)
            @Location("/{agentId}/system-settings")
            data class SystemSettings(val agentId: String)

            @Group(AGENT_PLUGIN)
            @Location("/{agentId}/plugins")
            data class Plugins(val agentId: String)

            @Group(AGENT_PLUGIN)
            @Location("/{agentId}/plugins/{pluginId}")
            data class Plugin(val agentId: String, val pluginId: String)

            @Group(AGENT_PLUGIN)
            @Location("/{agentId}/plugins/{pluginId}/dispatch-action")
            data class DispatchPluginAction(val agentId: String, val pluginId: String)

            @Group(AGENT_PLUGIN)
            @Location("/{agentId}/plugins/{pluginId}/reset")
            data class ResetPlugin(val agentId: String, val pluginId: String)

            @Group(AGENT_PLUGIN)
            @Location("/{agentId}/plugins/{pluginId}/toggle")
            data class TogglePlugin(val agentId: String, val pluginId: String)

            @Group(AGENT_PLUGIN)
            @Location("/{agentId}/plugins/{pluginId}/data/{dataType}")
            data class PluginData(val agentId: String, val pluginId: String, val dataType: String)
        }

        @Group(SERVICE_GROUP)
        @Location("/service-groups/{serviceGroupId}")
        data class ServiceGroup(val serviceGroupId: String) {
            @Group(SERVICE_GROUP)
            @Location("/plugins/{pluginId}")
            data class Plugin(val serviceGroupParent: ServiceGroup, val pluginId: String) {
                @Group(SERVICE_GROUP)
                @Location("/dispatch-action")
                data class DispatchAction(val parent: Plugin)

                @Group(SERVICE_GROUP)
                @Location("/data/{dataType}")
                data class Data(val parent: Plugin, val dataType: String)
            }
        }

        //TODO rewrite notifications
        @Group(SYSTEM)
        @Location("/notifications/read")
        class ReadNotification

        @Group(SYSTEM)
        @Location("/notifications/delete")
        class DeleteNotification
    }

}
