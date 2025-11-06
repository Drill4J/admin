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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

sealed interface Event<out T> {
    data class Value<T>(val data: T) : Event<T>
    data object Done : Event<Nothing>
}

class CompletableSharedFlow<T>(
    replay: Int = 0,
    extraBufferCapacity: Int = 0
) : FlowCollector<T>, Flow<T> {
    private val _flow = MutableSharedFlow<Event<T>>(replay, extraBufferCapacity)
    private val flow: Flow<T> = _flow
        .takeWhile { it !is Event.Done }
        .mapNotNull { (it as? Event.Value)?.data }

    override suspend fun emit(value: T) = _flow.emit(Event.Value(value))
    override suspend fun collect(collector: FlowCollector<T>) = flow.collect(collector)

    suspend fun waitForSubscribers(subscribersCount: Int) = withTimeoutOrNull(1.seconds) {
        _flow.subscriptionCount.filter { it == subscribersCount }.first()
    }

    suspend fun complete() = _flow.emit(Event.Done)
}
