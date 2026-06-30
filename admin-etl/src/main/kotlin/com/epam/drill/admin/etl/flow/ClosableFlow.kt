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
package com.epam.drill.admin.etl.flow

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import java.util.concurrent.atomic.AtomicBoolean

class ClosableFlow<T>(
    private val block: suspend FlowCollector<T>.() -> Unit,
    private val onClose: suspend () -> Unit
) : Flow<T> {

    override suspend fun collect(collector: FlowCollector<T>) {
        block(collector)
    }

    suspend fun close() {
        onClose()
    }
}

fun <T> Iterable<T>.asClosableFlow(): ClosableFlow<T> {
    val stopSignal = AtomicBoolean(false)
    return ClosableFlow(
        block = {
            for (item in this@asClosableFlow) {
                if (stopSignal.get()) break
                emit(item)
            }
        },
        onClose = {
            stopSignal.set(true)
        }
    )
}