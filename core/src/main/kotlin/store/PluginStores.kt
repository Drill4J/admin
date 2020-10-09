package com.epam.drill.admin.store

import com.epam.kodux.*
import jetbrains.exodus.entitystore.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import java.io.*

class PluginStores(private val baseDir: File) : Closeable {

    private val _stores = atomic(
        persistentMapOf<String, Lazy<StoreClient>>()
    )

    operator fun get(pluginId: String): StoreClient = _stores.updateAndGet { map ->
        map.takeIf { pluginId in it } ?: map.put(pluginId, lazy {
            val dir = baseDir.resolve(pluginId).resolve("store").apply { mkdirs() }
            StoreClient(PersistentEntityStores.newInstance(dir))
        })
    }.getValue(pluginId).value

    override fun close() {
        _stores.value.values.forEach {
            it.value.closeStore()
        }
    }
}
