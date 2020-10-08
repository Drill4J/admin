package com.epam.drill.admin.store

import com.epam.kodux.*
import jetbrains.exodus.entitystore.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import java.io.*
import java.util.*
import kotlin.test.*

class MessagePersistingTest {

    @Serializable
    data class SimpleMessage(val s: String)

    private val storageDir = File("build/tmp/test/stores/${this::class.simpleName}-${UUID.randomUUID()}")

    private val storeClient = StoreClient(PersistentEntityStores.newInstance(storageDir))

    @AfterTest
    fun cleanStore() {
        storeClient.store.close()
        storageDir.deleteRecursively()
    }

    @Test
    fun `storeMessage - readMessage`() {
        val message = SimpleMessage("data")
        runBlocking {
            assertNull(storeClient.readMessage("1"))
            storeClient.storeMessage("1", message)
            assertEquals(message, storeClient.readMessage("1"))
        }
    }
}
