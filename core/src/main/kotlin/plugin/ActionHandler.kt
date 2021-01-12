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
