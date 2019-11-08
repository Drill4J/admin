package com.epam.drill.e2e

import com.epam.kodux.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.io.*
import java.io.*

abstract class AdminTest {

    @TempDir
    lateinit var projectDir: File
    lateinit var globToken: String
    lateinit var storeManager: StoreManager

    @AfterEach
    fun closeResources() {
        storeManager.storages.forEach { it.value.close() }
    }
}