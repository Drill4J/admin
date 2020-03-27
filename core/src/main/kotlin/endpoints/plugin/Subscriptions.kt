package com.epam.drill.admin.endpoints.plugin

import com.epam.drill.plugin.api.end.*
import kotlinx.serialization.*

@Serializable
sealed class Subscription {
    abstract fun toKey(destination: String = ""): String
}

@Serializable
@SerialName("AGENT")
data class AgentSubscription(
    val agentId: String,
    val buildVersion: String? = null
) : Subscription() {
    override fun toKey(destination: String) = "agent::$agentId:$buildVersion/$destination"
}

@Serializable
@SerialName("GROUP")
data class GroupSubscription(
    val groupId: String
) : Subscription() {
    override fun toKey(destination: String) = "group::$groupId/$destination"
}

internal fun SendContext.toSubscription(): Subscription = when (this) {
    is AgentSendContext -> AgentSubscription(agentId, buildVersion)
    is GroupSendContext -> GroupSubscription(groupId)
    else -> error("Unknown send context $this")
}
