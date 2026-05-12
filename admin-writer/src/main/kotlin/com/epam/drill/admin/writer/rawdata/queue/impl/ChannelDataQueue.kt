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

import com.epam.drill.admin.writer.rawdata.queue.DataQueue
import com.epam.drill.admin.writer.rawdata.route.payload.RawDataPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.withTimeout
import mu.KotlinLogging
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class ChannelDataQueue<T: RawDataPayload>(
    private val deserializer: suspend (KClass<out T>, ByteArray) -> T,
    capacity: Int = Channel.BUFFERED,
    private val shutdownTimeout: Duration = 5.seconds,
) : DataQueue<T>, Channel<T> by Channel(capacity), AutoCloseable {
    private val logger = KotlinLogging.logger {}
    private val producerChannel = Channel<Pair<KClass<out T>, ByteArray>>(Channel.RENDEZVOUS)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        scope.launch {
            for ((type, bytes) in producerChannel) {
                runCatching { deserializer(type, bytes) }
                    .onFailure { e ->
                        logger.error(e) { "Error while deserialization queue for [$type]" }
                    }.getOrNull()?.let { i ->
                        this@ChannelDataQueue.send(i)
                    } ?: continue
            }
        }
    }

    override suspend fun enqueue(type: KClass<out T>, data: ByteArray) {
        producerChannel.send(type to data)
    }

    override suspend fun dequeue(): T {
        return this.receive()
    }

    override fun close() {
        producerChannel.close()
        this.close()
        runBlocking {
            withTimeout(shutdownTimeout.toJavaDuration()) {
                scope.coroutineContext[Job]?.children?.forEach { it.join() }
            }
            scope.cancel()
        }
    }
}