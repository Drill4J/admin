package com.epam.drill.e2e

import com.epam.drill.agentmanager.*
import com.epam.kodux.*
import io.ktor.server.testing.*
import kotlinx.coroutines.channels.*
import org.junit.jupiter.api.*
import java.io.*
import java.util.*

abstract class AdminTest {
    var watcher: (suspend TestApplicationEngine.(Channel<Set<AgentInfoWebSocket>>) -> Unit?)? = null
    val projectDir = File(System.getProperty("java.io.tmpdir") + File.separator + UUID.randomUUID())

    lateinit var globToken: String
    lateinit var storeManager: StoreManager
    fun uiWatcher(bl: suspend TestApplicationEngine.(Channel<Set<AgentInfoWebSocket>>) -> Unit): AdminTest {
        this.watcher = bl
        return this
    }

    @AfterEach
    fun closeResources() {
        storeManager.storages.forEach { it.value.close() }
        storeManager.storages.clear()
    }
}