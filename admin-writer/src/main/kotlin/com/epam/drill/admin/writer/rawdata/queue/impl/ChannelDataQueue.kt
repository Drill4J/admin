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
import com.epam.drill.admin.writer.rawdata.queue.QueueInput
import com.epam.drill.admin.writer.rawdata.queue.QueueOutput
import com.epam.drill.admin.writer.rawdata.route.DataIngestRoute
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class ChannelDataQueue<R : DataIngestRoute, T : RawDataPayload>(
    private val deserializer: suspend (R, ByteArray) -> T,
    capacity: Int = Channel.BUFFERED,
    private val shutdownTimeout: Duration = 5.seconds,
) : DataQueue<R, T>, Channel<QueueOutput<T>> by Channel(capacity), AutoCloseable {
    private val logger = KotlinLogging.logger {}
    private val inputChannel = Channel<QueueInput<R>>(Channel.RENDEZVOUS)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        scope.launch {
            for (input in inputChannel) {
                runCatching {
                    deserializer(input.route, input.payload)
                }.onFailure { e ->
                    logger.error(e) { "Error while deserialization queue for [${input.route::class.simpleName}]: ${e.message}" }
                }.getOrNull()?.let { payload ->
                    this@ChannelDataQueue.send(QueueOutput(payload, input.metadata))
                } ?: continue
            }
        }
    }

    override suspend fun enqueue(input: QueueInput<R>) {
        inputChannel.send(input)
    }

    override suspend fun dequeue(): QueueOutput<T> {
        return this.receive()
    }

    override fun close() {
        inputChannel.close()
        this.close()
        runBlocking {
            withTimeout(shutdownTimeout.toJavaDuration()) {
                scope.coroutineContext[Job]?.children?.forEach { it.join() }
            }
            scope.cancel()
        }
    }
}