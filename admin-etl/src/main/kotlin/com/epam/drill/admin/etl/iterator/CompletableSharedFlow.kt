package com.epam.drill.admin.etl.iterator

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withTimeout
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
    val flow: Flow<T> = _flow
        .takeWhile { it !is Event.Done }
        .mapNotNull { (it as? Event.Value)?.data }

    override suspend fun emit(value: T) = _flow.emit(Event.Value(value))
    override suspend fun collect(collector: FlowCollector<T>) = flow.collect(collector)

    suspend fun waitForSubscribers(subscribersCount: Int) = withTimeoutOrNull(1.seconds) {
        _flow.subscriptionCount.filter { it == subscribersCount }.first()
    }

    suspend fun complete() = _flow.emit(Event.Done)
}
