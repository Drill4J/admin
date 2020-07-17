package com.epam.drill.admin.plugin

import com.epam.drill.admin.cache.*
import com.epam.drill.admin.cache.type.*
import com.epam.drill.admin.endpoints.plugin.*

class PluginCache(
    cacheService: CacheService
) : Cache<Any, Any> by cacheService.ofType<PluginCache>()

internal fun PluginCache.getData(
    agentId: String,
    buildVersion: String,
    type: String
): Any? = AgentSubscription(agentId, buildVersion).let { this[it.toKey("/data/$type")] }
