package com.epam.drill.admin.plugin

import com.epam.drill.admin.cache.*
import com.epam.drill.admin.cache.type.*
import com.epam.drill.admin.endpoints.plugin.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.admin.store.*
import com.epam.drill.admin.websocket.*


typealias FrontMessage = Any

class PluginCaches(
    private val cacheService: CacheService,
    private val plugins: Plugins,
    private val pluginStores: PluginStores
) {

    internal operator fun get(pluginId: String): Cache<Any, FrontMessage> = cacheService.getOrCreate(pluginId)

    //TODO aggregate plugin data
    internal suspend fun getData(
        agentId: String,
        buildVersion: String,
        type: String
    ): Any? = plugins.keys.firstOrNull()?.let { pluginId ->
        val key = AgentSubscription(agentId, buildVersion).toKey("/data/$type")
        retrieveMessage(pluginId, key)
    }.takeIf { it  != "" }

    internal suspend fun retrieveMessage(
        pluginId: String,
        subscriptionKey: String
    ): FrontMessage = this[pluginId].let { cache ->
        cache[subscriptionKey] ?: run {
            val classLoader = plugins[pluginId]?.run {
                pluginClass.classLoader
            } ?: Thread.currentThread().contextClassLoader
            val messageFromStore = pluginStores[pluginId].readMessage(subscriptionKey, classLoader) ?: ""
            messageFromStore.also { cache[subscriptionKey] = it }
        }
    }
}

class PluginSessions(plugins: Plugins) {

    private val sessionCaches: Map<String, SessionStorage> = plugins.mapValues { SessionStorage() }

    operator fun get(pluginId: String): SessionStorage = sessionCaches.getValue(pluginId)
}
