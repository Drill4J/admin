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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class QueueProcessor<R : DataIngestRoute, T : RawDataPayload>(
    private val handler: suspend (QueueOutput<T>) -> Unit,
    private val onError: suspend (QueueOutput<T>, Throwable) -> Unit = { _, _ -> },
    private val onSuccess: suspend (QueueOutput<T>) -> Unit = {},
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun run(queue: DataQueue<R, T>, workers: Int = 1) {
        repeat(workers) { worker ->
            scope.launch {
                for (output in queue) {
                    runCatching {
                        handler(output)
                    }.onFailure { e ->
                        onError(output, e)
                    }.onSuccess {
                        onSuccess(output)
                    }
                }
            }
        }
    }
}
