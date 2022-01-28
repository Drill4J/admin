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
package com.epam.drill.admin.cache

import com.epam.drill.admin.cache.type.*
import kotlin.reflect.*

interface CacheService {
    fun <K, V> getOrCreate(id: Any, qualifier: Any = "", replace: Boolean = false): Cache<K, V>
}

operator fun <K, V> CacheService.getValue(thisRef: Any?, property: KProperty<*>): Cache<K, V> {
    return getOrCreate(property.name)
}
