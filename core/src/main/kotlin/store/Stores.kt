/**
 * Copyright 2020 EPAM Systems
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
package com.epam.drill.admin.store

import com.epam.kodux.*
import jetbrains.exodus.entitystore.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import java.io.*
import com.epam.dsm.StoreClient as DsmStoreClient

//todo remove
sealed class Stores(
    private val baseDir: File,
    private val subDir: String = "",
) : Closeable {

    private val _stores = atomic(
        persistentMapOf<String, Lazy<StoreClient>>()
    )

    operator fun get(id: String): StoreClient = _stores.updateAndGet { map ->
        map.takeIf { id in it } ?: map.put(id, lazy {
            val dir = baseDir.resolve(id).resolve(subDir)
            StoreClient(PersistentEntityStores.newInstance(dir)).apply {
                environment.environmentConfig.apply {
                    memoryUsagePercentage = 10
                    logCacheUseSoftReferences = true
                }
            }
        })
    }.getValue(id).value

    override fun close() {
        _stores.value.values.forEach {
            it.value.closeStore()
        }
    }
}

/**
 * it duplicates as val agentStoresDSM = DsmStoreClient("agents")
 * todo remove
 */
class AgentStores(baseDir: File) : Stores(baseDir.resolve("agents")) {
    fun agentStore(agentId: String): StoreClient = get(agentId)
}

//todo remove
//class PluginStores(baseDir: File) : Stores(baseDir, "store")


//pluginId = new schema? or
//val pluginStores = DsmStoreClient("plugins")
//todo use singleton or smth else
fun pluginStoresDSM(pluginId: String): DsmStoreClient = DsmStoreClient("plugins_$pluginId")

//object pluguns : DsmStoreClient()
//class AgentStoresPostgres() : DsmStoreClient("plugin"){
//
//}

val agentStoresDSM = DsmStoreClient("agents")
