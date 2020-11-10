package com.epam.drill.admin.plugin

import com.epam.drill.admin.api.websocket.*
import com.epam.drill.admin.cache.*
import com.epam.drill.admin.cache.type.*
import com.epam.drill.admin.plugins.*
import com.epam.drill.admin.store.*
import com.epam.drill.admin.websocket.*

class PluginCaches(
    private val cacheService: CacheService,
    private val plugins: Plugins,
    private val pluginStores: PluginStores
) {
    private data class AgentKey(val pluginId: String, val agentId: String)
    private data class GroupKey(val pluginId: String, val groupId: String)

    internal fun get(
        pluginId: String,
        subscription: Subscription?,
        replace: Boolean = false
    ): Cache<Any, FrontMessage> = when (subscription) {
        is AgentSubscription -> cacheService.getOrCreate(
            id = AgentKey(pluginId, subscription.agentId),
            qualifier = subscription.buildVersion ?: "",
            replace = replace
        )
        is GroupSubscription -> cacheService.getOrCreate(GroupKey(pluginId, subscription.groupId))
        null -> cacheService.getOrCreate(pluginId)
    }

    //TODO aggregate plugin data
    internal suspend fun getData(
        agentId: String,
        buildVersion: String,
        type: String
    ): Any? = plugins.keys.firstOrNull()?.let { pluginId ->
        retrieveMessage(
            pluginId,
            AgentSubscription(agentId, buildVersion),
            "/data/$type"
        )
    }.takeIf { it != "" }

    internal suspend fun retrieveMessage(
        pluginId: String,
        subscription: Subscription?,
        destination: String
    ): FrontMessage = get(pluginId, subscription).let { cache ->
        cache[destination] ?: run {
            val messageKey = subscription.toKey(destination)
            val classLoader = plugins[pluginId]?.run {
                pluginClass.classLoader
            } ?: Thread.currentThread().contextClassLoader
            val messageFromStore = pluginStores[pluginId].readMessage(messageKey, classLoader) ?: ""
            messageFromStore.also { cache[destination] = it }
        }
    }
}

class PluginSessions(plugins: Plugins) {

    private val sessionCaches: Map<String, SessionStorage> = plugins.mapValues { SessionStorage() }

    operator fun get(pluginId: String): SessionStorage = sessionCaches.getValue(pluginId)
}
