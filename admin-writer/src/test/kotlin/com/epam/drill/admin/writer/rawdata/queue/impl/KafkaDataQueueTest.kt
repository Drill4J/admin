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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Testcontainers
class KafkaDataQueueTest {

    companion object {
        @Container
        @JvmField
        val kafka: ConfluentKafkaContainer =
            ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"))
    }

    private fun uniqueTopic() = "test-topic-${UUID.randomUUID()}"
    private fun uniqueGroupId() = "test-group-${UUID.randomUUID()}"

    private fun createQueue(
        topic: String = uniqueTopic(),
        recordKeyToPayloadType: (String) -> KClass<out BuildPayload> = {
            when (it) {
                "builds" -> BuildPayload::class
                else -> throw IllegalArgumentException("Unknown record key: $it")
            }
        },
        routeToRecordKey: (BuildsRoute) -> String = { "builds" },
    ) = KafkaDataQueue.create(
        bootstrapServers = kafka.bootstrapServers,
        topic = topic,
        consumerGroupId = uniqueGroupId(),
        deserializer = ::json,
        recordKeyToPayloadType = recordKeyToPayloadType,
        routeToRecordKey = routeToRecordKey,
        capacity = Channel.UNLIMITED,
        pollTimeout = 500.milliseconds,
        shutdownTimeout = 5.seconds,
        metrics = RawDataMeter(SimpleMeterRegistry()),
    )


    @Test
    fun `enqueue and dequeue should deliver payload`() {
        val queue = createQueue()
        val testBytes = BuildPayload(groupId = "my-group", appId = "my-app", buildVersion = "1.0.0").toBytes()
        val testMetadata = mapOf("key-1" to "value-1")

        runBlocking {
            queue.enqueue(QueueInput(BuildsRoute(), testBytes, testMetadata))
            val output = withTimeout(10_000) { queue.dequeue() }

            assertEquals("my-group", output.payload.groupId)
            assertEquals("my-app", output.payload.appId)
            assertEquals("value-1", output.metadata["key-1"])
        }

        queue.close()
    }

    @Test
    fun `close should stop consuming and mark channel as closed`() {
        val queue = createQueue()

        queue.close()

        runBlocking {
            assertFailsWith<IllegalStateException>("Cannot perform operation after producer has been closed") {
                queue.enqueue(QueueInput(BuildsRoute(), ByteArray(0), emptyMap()))
            }
        }
    }

    private fun BuildPayload.toBytes() =
        jsonConfig.encodeToString(BuildPayload.serializer(), this).toByteArray(Charsets.UTF_8)
}



