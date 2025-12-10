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

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

class StoppableFlowImpl<out T>(
    flow: Flow<T>,
    private val signal: MutableSharedFlow<Unit> = MutableSharedFlow()
): StoppableFlow<T> {
    private val _flow: Flow<T> = flow.takeUntil(signal)

    override suspend fun stop() {
        signal.emit(Unit)
    }

    override suspend fun collect(collector: FlowCollector<T>) {
        _flow.collect(collector)
    }
}

fun <T> Flow<T>.stoppable(): StoppableFlow<T> = StoppableFlowImpl(this)

fun <T> Flow<T>.takeUntil(other: Flow<*>): Flow<T> = channelFlow {
    val scope = this
    val mainJob = launch {
        try {
            collect { send(it) }
        } finally {
            scope.close()
        }
    }
    val stopperJob = launch {
        other.collect {
            mainJob.cancel()
            scope.close()
        }
    }
    awaitClose {
        mainJob.cancel()
        stopperJob.cancel()
    }
}