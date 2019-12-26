package com.epam.drill.admin.store

import com.epam.kodux.*
import jetbrains.exodus.entitystore.*
import java.io.*

class CommonStore(private val baseDir: File) {
    val client by lazy {
        val store = PersistentEntityStores.newInstance(baseDir.resolve("common"))
        StoreClient(store)
    }
}
