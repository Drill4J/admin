package com.epam.drill.admin.router

import io.ktor.locations.*

object WsRoutes {
    @Location("/service-groups/{groupId}")
    data class ServiceGroup(val groupId: String)

    @Location("/service-groups/{groupId}/plugins")
    data class ServiceGroupPlugins(val groupId: String)

    @Location("/agents")
    class Agents

    @Location("/agents/{agentId}/plugins")
    data class AgentPlugins(val agentId: String)

    @Location("/agents/{agent}/plugins/{plugin}/config")
    data class AgentPluginConfig(val agent: String, val plugin: String)

    @Location("/agents/{agentId}")
    data class Agent(val agentId: String)

    @Location("/plugins")
    class Plugins

    @Location("/agents/{agentId}/builds")
    data class AgentBuilds(val agentId: String)
}
