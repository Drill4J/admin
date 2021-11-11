/**
 * Copyright 2020 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.drill.admin.store

import com.epam.drill.admin.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.junit.jupiter.api.*
import ru.yandex.qatools.embed.postgresql.*
import kotlin.test.*
import kotlin.test.Test

class MessagePersistingTest {

    @Serializable
    data class SimpleMessage(val s: String)

    @Test
    fun `storeMessage - readMessage`() {
        val storeClient = pluginStoresDSM("${MessagePersistingTest::class.simpleName}")
        val message = SimpleMessage("data")
        runBlocking {
            assertNull(storeClient.readMessage("1"))
            storeClient.storeMessage("1", message)
            assertEquals(message, storeClient.readMessage("1"))
        }
    }

    companion object {
        lateinit var postgres: EmbeddedPostgres

        @BeforeAll
        @JvmStatic
        fun connectDB() {
            postgres = EmbeddedPostgres(embeddedVersion)
            val host = "localhost"
            val port = 5438
            val dbName = "dbName"
            val userName = "userName"
            val password = "password"
            postgres.start(
                host,
                port,
                dbName,
                userName,
                password
            )
            Database.connect(
                "jdbc:postgresql://$host:$port/$dbName", driver = "org.postgresql.Driver",
                user = userName, password = password
            ).also {
                println { "Connected to db ${it.url}" }
            }
        }

        @AfterAll
        @JvmStatic
        fun close() {
            postgres.close()
        }
    }


}
