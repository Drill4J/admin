package com.epam.drill.admin.endpoints

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.plugin.api.*
import com.epam.drill.plugin.api.end.*
import com.epam.kodux.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import java.io.*
import java.lang.reflect.*

class AgentEntry(agent: AgentInfo) {

    private val _agent = atomic(agent)

    private val _instanceMap = atomic(
        persistentHashMapOf<String, AdminPluginPart<*>>()
    )

    var agent: AgentInfo
        get() = _agent.value
        set(value) = _agent.update { value }

    val plugins get() = _instanceMap.value.values

    fun updateAgent(
        updater: (AgentInfo) -> AgentInfo
    ): AgentInfo = _agent.updateAndGet(updater)

    operator fun get(pluginId: String): AdminPluginPart<*>? = _instanceMap.value[pluginId]

    suspend fun get(
        pluginId: String,
        updater: suspend AgentEntry.() -> AdminPluginPart<*>
    ): AdminPluginPart<*> = get(pluginId) ?: _instanceMap.updateAndGet {
        it.takeIf { pluginId in it } ?: it.put(pluginId, updater())
    }.getValue(pluginId)

    fun close() {
        plugins.forEach { plugin ->
            runCatching { (plugin as? Closeable)?.close() }
        }
    }
}

internal fun Plugin.createInstance(
    agentInfo: AgentInfo,
    data: AdminData,
    sender: Sender,
    store: StoreClient
): AdminPluginPart<*> {
    @Suppress("UNCHECKED_CAST")
    val constructor = pluginClass.constructors.run {
        first() as Constructor<out AdminPluginPart<*>>
    }
    val classToArg: (Class<*>) -> Any = {
        when (it) {
            String::class.java -> pluginBean.id
            CommonAgentInfo::class.java -> agentInfo.toCommonInfo()
            AdminData::class.java -> data
            Sender::class.java -> sender
            StoreClient::class.java -> store
            else -> error("${pluginClass.name}: unsupported constructor parameter type $it.")
        }
    }
    val args: Array<Any> = constructor.parameterTypes.map(classToArg).toTypedArray()
    return constructor.newInstance(*args)
}

internal suspend fun AgentEntry.applyPackagesChanges() {
    for (pluginId in agent.plugins) {
        this[pluginId]?.applyPackagesChanges()
    }
}
