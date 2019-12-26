package com.epam.drill.router

import io.ktor.locations.*

object WsRoutes {
    @Location("/get-all-agents")
    class GetAllAgents

    @Location("/service-group/{groupId}")
    data class ServiceGroup(val groupId: String)

    @Location("/get-agent/{agentId}")
    data class GetAgent(val agentId: String)

    @Location("/agent/{agentId}/get-builds")
    data class GetAgentBuilds(val agentId: String)

    @Location("/get-all-plugins")
    class GetAllPlugins

    @Location("/{agentId}/get-plugin-info")
    data class GetPluginInfo(val agentId: String)

    @Location("/{agent}/{plugin}/config")
    data class GetPluginConfig(val agent: String, val plugin: String)

    @Location("/notifications")
    class GetNotifications()

    @Location("/{agentId}/builds")
    data class GetBuilds(val agentId: String)
}
