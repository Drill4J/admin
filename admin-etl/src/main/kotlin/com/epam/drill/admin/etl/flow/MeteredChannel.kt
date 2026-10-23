package com.epam.drill.admin.etl.flow

import io.micrometer.core.instrument.Counter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelIterator

class MeteredChannel<T>(
    capacity: Int,
    private val beforeSendCounter: Counter,
    private val afterReceiveCounter: Counter,
    private val delegate: Channel<T> = Channel(capacity),
) : Channel<T> by delegate {
    override suspend fun send(element: T) {
        beforeSendCounter.increment()
        delegate.send(element)
    }

    override suspend fun receive(): T {
        return delegate.receive().also { afterReceiveCounter.increment() }
    }

    override fun iterator(): ChannelIterator<T> {
        val it = delegate.iterator()
        return object : ChannelIterator<T> {
            override suspend fun hasNext(): Boolean = it.hasNext()

            override fun next(): T = it.next().also {
                afterReceiveCounter.increment()
            }
        }
    }

}