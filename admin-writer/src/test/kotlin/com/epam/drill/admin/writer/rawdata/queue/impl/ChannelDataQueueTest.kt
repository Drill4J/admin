/**
 * Copyright 2020 - 2022 EPAM Systems
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
package com.epam.drill.admin.writer.rawdata.queue.impl

import com.epam.drill.admin.writer.rawdata.config.RawDataMeter
import com.epam.drill.admin.writer.rawdata.queue.QueueInput
import com.epam.drill.admin.writer.rawdata.route.BuildsRoute
import com.epam.drill.admin.writer.rawdata.route.jsonConfig
import com.epam.drill.admin.writer.rawdata.route.payload.BuildPayload
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.mockito.kotlin.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class ChannelDataQueueTest {

    @Test
    fun `enqueue should deserialize payload and make it available via dequeue`() {
        val queue = ChannelDataQueue<BuildsRoute, BuildPayload>(
            deserializer = ::json,
            routeToPayloadType = { BuildPayload::class },
            capacity = Channel.UNLIMITED,
            shutdownTimeout = 1.seconds,
            metrics = RawDataMeter(SimpleMeterRegistry()),
        )
        val testBytes = BuildPayload(groupId = "my-group", appId = "my-app", buildVersion = "1.0.0").toBytes()
        val testMetadata = mapOf("key-1" to "value-1")

        runBlocking {
            queue.enqueue(QueueInput(BuildsRoute(), testBytes, testMetadata))
            val output = withTimeout(2_000) { queue.dequeue() }

            assertEquals("my-group", output.payload.groupId)
            assertEquals("my-app", output.payload.appId)
            assertEquals(testMetadata, output.metadata)
        }

        queue.close()
    }

    @Test
    fun `close should close the output channel and prevent further enqueueing`() {
        val queue = ChannelDataQueue<BuildsRoute, BuildPayload>(
            deserializer = ::json,
            routeToPayloadType = { BuildPayload::class },
            capacity = Channel.UNLIMITED,
            shutdownTimeout = 1.seconds,
            metrics = RawDataMeter(SimpleMeterRegistry()),
        )

        queue.close()

        runBlocking {
            assertFailsWith<ClosedSendChannelException> {
                queue.enqueue(QueueInput(BuildsRoute(), ByteArray(0), emptyMap()))
            }
        }
    }

    private fun BuildPayload.toBytes() =
        jsonConfig.encodeToString(BuildPayload.serializer(), this).toByteArray(Charsets.UTF_8)
}