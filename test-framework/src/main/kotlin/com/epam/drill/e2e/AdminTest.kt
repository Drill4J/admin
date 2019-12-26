package com.epam.drill.e2e

import com.epam.drill.admin.store.*
import com.epam.drill.agentmanager.*
import com.epam.kodux.*
import io.ktor.server.testing.*
import jetbrains.exodus.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.sync.*
import org.junit.jupiter.api.*
import java.io.*
import java.util.*

abstract class AdminTest {
    val mut = Mutex()
    var watcher: (suspend TestApplicationEngine.(Channel<Set<AgentInfoWebSocket>>) -> Unit?)? = null
    val projectDir = File(System.getProperty("java.io.tmpdir") + File.separator + UUID.randomUUID())

    lateinit var engine: TestApplicationEngine
    lateinit var globToken: String
    lateinit var storeManager: StoreManager
    lateinit var commonStore: CommonStore

    fun uiWatcher(bl: suspend TestApplicationEngine.(Channel<Set<AgentInfoWebSocket>>) -> Unit): AdminTest {
        this.watcher = bl
        return this
    }

    @AfterEach
    fun closeResources() {
        storeManager.storages.forEach {
            try {
                it.value.close()
            } catch (ignored: ExodusException) {
            }
        }
        storeManager.storages.clear()
        try {
            commonStore.client.close()
        } catch (ignored: ExodusException) {
        } catch (ignored: UninitializedPropertyAccessException) {//FIXME get rid of this lateinit complexity
        }
    }
}
