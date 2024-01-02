package com.epam.drill.admin.auth.service.impl

import com.epam.drill.admin.auth.service.ApiKeyCacheService
import com.github.benmanes.caffeine.cache.Cache
import io.ktor.auth.*

/**
 * Implementation of [ApiKeyCacheService], providing caching functionalities.
 *
 * @property cache An instance of Caffeine`s [Cache] that stores key-value pairs, where keys are of type [String] and values are [Principal] objects.
 */
class CoffeineCacheService(
    val cache: Cache<String, Principal>
) : ApiKeyCacheService {
    override suspend fun getFromCacheOrPutIfAbsent(
        apiKey: String,
        ifMissCallback: suspend (String) -> Principal?
    ): Principal? {
        var principal = cache.getIfPresent(apiKey)
        if (principal == null) {
            principal = ifMissCallback.invoke(apiKey)
            principal?.let { cache.put(apiKey, principal) }
        }
        return principal
    }
}
