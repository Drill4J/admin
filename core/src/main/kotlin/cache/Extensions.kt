package com.epam.drill.admin.cache

import com.epam.drill.admin.cache.type.*
import kotlin.reflect.*

inline fun <reified T> CacheService.ofType(): Cache<Any, Any> = getOrCreate("${typeOf<T>()}")
