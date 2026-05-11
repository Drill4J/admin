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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class ChannelDataQueue<T>(
    private val consumer: suspend (T) -> Unit,
    private val onError: (T, Throwable) -> Unit = { _, _ -> },
    private val onSuccess: (T) -> Unit = { _, -> },
    capacity: Int = Channel.RENDEZVOUS,
    concurrency: Int = 1,
    private val shutdownTimeout: Duration = 5.seconds
) : DataQueue<T> {
    private val channel = Channel<T>(capacity)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        repeat(concurrency) {
            scope.launch {
                for (item in channel) {
                    runCatching { consumer(item) }
                        .onFailure { onError(item, it) }
                        .onSuccess { onSuccess(item)  }
                }
            }
        }
    }

    override suspend fun enqueue(data: T) {
        channel.send(data)
    }

    override fun close() {
        channel.close()
        runBlocking {
            withTimeout(shutdownTimeout.toJavaDuration()) {
                scope.coroutineContext[Job]?.children?.forEach { it.join() }
            }
            scope.cancel()
        }
    }
}