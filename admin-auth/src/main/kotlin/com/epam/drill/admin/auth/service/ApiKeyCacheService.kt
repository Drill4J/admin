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
package com.epam.drill.admin.auth.service

import io.ktor.auth.*

/**
 *  A service that manages the storage and retrieval of apiKeys.
 */
interface ApiKeyCacheService {
    /**
     * Retrieves a [Principal] object associated with the given [apiKey] from the cache.
     * If the key is not present in the cache, it invokes the [ifMissCallback] function to retrieve the value,
     * suspends during its execution, and then stores the result in the cache before returning it.
     *
     * @param apiKey The key is used to look up the value in the cache.
     * @param ifMissCallback A suspend function that is called to retrieve the value if it is not found in the cache.
     *                  Takes the key as a parameter and returns a [Principal] or null.
     * @return The [Principal] associated with the given key, either retrieved from the cache or obtained
     *         through the [getIfMiss] function. Returns null if the key is not found and [getIfMiss] returns null.
     */
    suspend fun getFromCacheOrPutIfAbsent(apiKey: String, ifMissCallback: suspend (String) -> Principal?): Principal?
}
