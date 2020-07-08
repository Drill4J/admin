package com.epam.drill.admin.plugin

import com.epam.drill.admin.cache.*
import com.epam.drill.admin.cache.type.*

class PluginCache(
    cacheService: CacheService
) : Cache<Any, Any> by cacheService.ofType<PluginCache>()
