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