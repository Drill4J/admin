package com.epam.drill.admin.store

import com.epam.kodux.*
import jetbrains.exodus.entitystore.*
import java.io.*

class CommonStore(private val baseDir: File) : Closeable {

    val client: StoreClient get() = _client.value

    private val _client: Lazy<StoreClient> = lazy {
        val store = PersistentEntityStores.newInstance(baseDir.resolve("common"))
        StoreClient(store)
    }


    override fun close() {
        if (_client.isInitialized()) {
            client.closeStore()
        }
    }
}
