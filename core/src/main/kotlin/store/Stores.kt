package com.epam.drill.admin.store

import com.epam.kodux.*
import jetbrains.exodus.entitystore.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import java.io.*

sealed class Stores(
    private val baseDir: File,
    private val subDir: String = ""
) : Closeable {

    private val _stores = atomic(
        persistentMapOf<String, Lazy<StoreClient>>()
    )

    operator fun get(id: String): StoreClient = _stores.updateAndGet { map ->
        map.takeIf { id in it } ?: map.put(id, lazy {
            val dir = baseDir.resolve(id).resolve(subDir)
            StoreClient(PersistentEntityStores.newInstance(dir))
        })
    }.getValue(id).value

    override fun close() {
        _stores.value.values.forEach {
            it.value.closeStore()
        }
    }
}

class AgentStores(baseDir: File) : Stores(baseDir.resolve("agents")) {
    fun agentStore(agentId: String): StoreClient = get(agentId)
}

class PluginStores(baseDir: File) : Stores(baseDir, "store")

