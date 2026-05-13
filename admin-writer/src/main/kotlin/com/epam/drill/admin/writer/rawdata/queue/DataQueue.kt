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
package com.epam.drill.admin.writer.rawdata.queue

import com.epam.drill.admin.writer.rawdata.route.DataIngestRoute
import com.epam.drill.admin.writer.rawdata.route.payload.RawDataPayload
import kotlinx.coroutines.channels.ReceiveChannel

interface DataQueue<R : DataIngestRoute, T : RawDataPayload> : ReceiveChannel<QueueOutput<T>> {
    suspend fun enqueue(input: QueueInput<R>)
    suspend fun dequeue(): QueueOutput<T>
}

class QueueInput<R : DataIngestRoute>(
    val route: R,
    val payload: ByteArray,
    val metadata: Map<String, String> = emptyMap()
)

class QueueOutput<T : RawDataPayload>(
    val payload: T,
    val metadata: Map<String, String> = emptyMap()
)