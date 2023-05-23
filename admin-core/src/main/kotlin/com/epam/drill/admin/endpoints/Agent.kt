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
package com.epam.drill.admin.endpoints

import com.epam.drill.admin.agent.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.plugin.api.*
import com.epam.drill.plugin.api.end.*
import com.epam.dsm.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import mu.*
import java.io.*
import java.lang.reflect.*

/**
 * all information related to the agent
 */
class Agent(info: AgentInfo) {
    private val logger = KotlinLogging.logger { }

    private val _info = atomic(info)

    private val _instanceMap = atomic(
        persistentHashMapOf<String, AdminPluginPart<*>>()
    )

    var info: AgentInfo
        get() = _info.value
        set(value) = _info.update { value }

    val plugins get() = _instanceMap.value.values

    fun update(
        updater: (AgentInfo) -> AgentInfo,
    ): AgentInfo = _info.updateAndGet(updater)

    operator fun get(pluginId: String): AdminPluginPart<*>? = _instanceMap.value[pluginId]

    fun get(
        pluginId: String,
        updater: Agent.() -> AdminPluginPart<*>,
    ): AdminPluginPart<*> = get(pluginId) ?: _instanceMap.updateAndGet {
        it.takeIf { pluginId in it } ?: it.put(pluginId, updater())
    }.getValue(pluginId)

    fun close() {
        plugins.forEach { plugin ->
            runCatching { (plugin as? Closeable)?.close() }
        }
    }
}

/**
 * Create a new instance of admin plugin part
 * @param agentInfo the information about the agent
 * @param data the admin part of information about the agent
 * @param sender the service for sending messages to the plugin
 * @param store the datasource client for persisting the plugin information
 * @return a new instance of admin plugin part
 *
 * @features Agent registration
 */
internal fun Plugin.createInstance(
    agentInfo: AgentInfo,
    data: AdminData,
    sender: Sender,
    store: StoreClient,
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

internal suspend fun Agent.applyPackagesChanges() {
    for (pluginId in info.plugins) {
        this[pluginId]?.applyPackagesChanges()
    }
}
