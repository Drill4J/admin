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
package com.epam.drill.admin.auth.service.impl

import com.epam.drill.admin.auth.service.ApiKeyCacheService
import com.github.benmanes.caffeine.cache.Cache
import io.ktor.auth.*

/**
 * Implementation of [ApiKeyCacheService], providing caching functionalities.
 *
 * @property cache An instance of Caffeine`s [Cache] that stores key-value pairs, where keys are of type [String] and values are [Principal] objects.
 */
class CaffeineCacheService(
    private val cache: Cache<String, Principal>
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
