package com.epam.drill.admin.endpoints

import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import com.epam.drill.plugin.api.end.*
import com.epam.kodux.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*

class AgentEntry(
    agent: AgentInfo,
    val agentSession: AgentWsSession
) {
    //TODO make agent immutable
    private val _agent = atomic(agent)

    private val _instanceMap = atomic(persistentHashMapOf<String, AdminPluginPart<*>>())

    var agent: AgentInfo
        get() = _agent.value
        set(value) = _agent.update { value }

    val plugins get() = _instanceMap.value.values

    operator fun get(pluginId: String): AdminPluginPart<*>? = _instanceMap.value[pluginId]

    suspend fun get(pluginId: String, updater: suspend AgentEntry.() -> AdminPluginPart<*>): AdminPluginPart<*> {
        return get(pluginId) ?: _instanceMap.updateAndGet { it.put(pluginId, updater()) }[pluginId]!!
    }
}

fun Plugin.createInstance(
    agentInfo: AgentInfo,
    data: AdminData,
    sender: Sender,
    store: StoreClient
): AdminPluginPart<*> {
    val constructor = pluginClass.getConstructor(
        AdminData::class.java,
        Sender::class.java,
        StoreClient::class.java,
        AgentInfo::class.java,
        String::class.java
    )
    return constructor.newInstance(
        data,
        sender,
        store,
        agentInfo,
        pluginBean.id
    )
}

suspend fun AgentEntry.applyPackagesChanges() {
    for (pluginMeta in agent.plugins) {
        this[pluginMeta.id]?.applyPackagesChanges()
    }
}
