package com.epam.drill.admin.api.routes

import io.ktor.locations.*

object WsRoot {
    @Location("/api/version")
    object Version

    @Location("/api/agents")
    class Agents

    @Location("/api/agents/{agentId}")
    class Agent(val agentId: String)

    @Location("/api/groups")
    class Groups

    @Location("/api/groups/{groupId}")
    class Group(val groupId: String)
}
