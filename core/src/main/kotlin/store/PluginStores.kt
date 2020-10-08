package com.epam.drill.admin.store

import com.epam.kodux.*
import jetbrains.exodus.entitystore.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import java.io.*

class PluginStores(private val baseDir: File) : Closeable {

    private val _stores = atomic(persistentMapOf<String, StoreClient>())

    operator fun get(pluginId: String): StoreClient = _stores.updateAndGet { map ->
        map.takeIf { pluginId in it } ?: map.run {
            val dir = baseDir.resolve(pluginId).resolve("store").apply { mkdirs() }
            put(pluginId, StoreClient(PersistentEntityStores.newInstance(dir)))
        }
    }.getValue(pluginId)

    override fun close() {
        _stores.value.forEach {
            it.value.closeStore()
        }
    }
}
