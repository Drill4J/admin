package com.epam.drill.admin.plugin

import com.epam.drill.admin.cache.*
import com.epam.drill.admin.cache.type.*
import com.epam.drill.admin.endpoints.agent.*
import com.epam.drill.admin.endpoints.plugin.*
import com.epam.drill.admin.plugins.*


class PluginCaches(
    private val cacheService: CacheService,
    private val plugins: Plugins
) {

    internal operator fun get(pluginId: String): Cache<Any, Any> = cacheService.getOrCreate(pluginId)

    //TODO aggregate plugin data
    internal fun getData(
        agentId: String,
        buildVersion: String,
        type: String
    ): Any? = plugins.keys.firstOrNull()?.let(::get)?.run {
        AgentSubscription(agentId, buildVersion).let { this[it.toKey("/data/$type")] }
    }
}

class PluginSessions(plugins: Plugins) {

    private val sessionCaches: Map<String, SessionStorage> = plugins.mapValues { SessionStorage() }

    operator fun get(pluginId: String): SessionStorage = sessionCaches.getValue(pluginId)
}
