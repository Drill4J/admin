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
package com.epam.drill.admin.websocket

import com.epam.drill.admin.api.websocket.*

internal const val agentPrefix = "agent::"
internal const val groupPrefix = "group::"

fun Subscription?.toKey(destination: String): String = when (this) {
    is AgentSubscription -> "${agentKeyPattern(agentId, buildVersion, filterId)}$destination"
    is GroupSubscription -> "$groupPrefix$groupId$destination"
    null -> destination
}

fun Subscription?.toAgentKey(): String = when (this) {
    is AgentSubscription -> "${agentKeyPattern(agentId, buildVersion, filterId)}"
    is GroupSubscription -> "$groupPrefix$groupId"
    null -> throw IllegalArgumentException("Invalid subscription type.")
}

internal fun agentKeyPattern(
    agentId: String,
    buildVersion: String?,
    filterId: String = "",
) = "$agentPrefix$agentId:$buildVersion:$filterId"
