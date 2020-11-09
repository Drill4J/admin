package com.epam.drill.admin.websocket

import com.epam.drill.admin.api.websocket.*

fun Subscription?.toKey(destination: String): String = when (this) {
    is AgentSubscription -> "agent::$agentId:$buildVersion$destination"
    is GroupSubscription -> "group::$groupId$destination"
    null -> destination
}
