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
package com.epam.drill.admin.plugin

import com.epam.drill.admin.common.serialization.*
import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.api.*
import com.epam.drill.plugin.api.end.*
import kotlinx.serialization.*
import kotlin.reflect.full.*

internal suspend fun AdminPluginPart<*>.processAction(
    action: String,
    agentSessions: (String) -> Iterable<AgentWsSession>
): Any = doRawAction(action).also { result ->
    (result as? ActionResult)?.agentAction?.let { action ->
        action.actionSerializerOrNull()?.let { serializer ->
            val actionStr = serializer stringify action
            val agentAction = PluginAction(id, actionStr)
            agentSessions(agentInfo.id).map {
                it.sendToTopic<Communication.Plugin.DispatchEvent, PluginAction>(agentAction)
            }.forEach { it.await() }
        }
    }
}

internal fun Any.actionSerializerOrNull(): KSerializer<Any>? = sequenceOf(
    this::class.superclasses.firstOrNull(),
    this::class
).mapNotNull {
    it?.serializerOrNull()
}.firstOrNull()?.let {
    @Suppress("UNCHECKED_CAST")
    it as KSerializer<Any>
}
