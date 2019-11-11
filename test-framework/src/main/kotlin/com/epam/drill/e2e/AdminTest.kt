package com.epam.drill.e2e

import com.epam.kodux.*
import org.junit.jupiter.api.*
import java.io.*
import java.util.*

abstract class AdminTest {

    val projectDir = File(System.getProperty("java.io.tmpdir") + File.separator + UUID.randomUUID())

    lateinit var globToken: String
    lateinit var storeManager: StoreManager

    @AfterEach
    fun closeResources() {
        storeManager.storages.forEach { it.value.close() }
        storeManager.storages.clear()
    }
}