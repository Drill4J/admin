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

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.*
import kotlin.time.Duration.Companion.seconds

/**
 * A hot channel-backed multicast flow.
 *
 * The class itself implements [FlowCollector] — it is the single producer
 * endpoint.  The producer emits once; every currently-registered subscriber
 * channel receives a copy.  Subscribers that join late miss whatever was
 * emitted before they called [subscribe].
 *
 * ### Architecture
 *
 * ```
 *   Producer
 *   (external caller, owns the lifecycle)
 *
 *   SubscribableChannelFlow : FlowCollector<T>
 *       │
 *       │  emit(value)
 *       │      ├─── Channel A (capacity) ───► subscribe() → Flow  [sub-A]
 *       │      ├─── Channel B (capacity) ───► subscribe() → Flow  [sub-B]
 *       │      └─── Channel C (capacity) ───► subscribe() → Flow  [sub-C, joined late → missed early items]
 *       │
 *       └── close() ──► closes all channels ──► all subscriber Flows complete
 * ```
 *
 * ### Contract
 *
 * | Concern              | Behaviour                                                          |
 * |----------------------|--------------------------------------------------------------------|
 * | **Hot**              | One producer run; [emit] fans out to all current subscribers.      |
 * | **Late subscriber**  | Misses all elements emitted before [subscribe] was called.         |
 * | **Slow subscriber**  | Producer suspends until every subscriber's buffer has room.        |
 * | **Close**            | [close] closes all channels; each subscriber Flow completes.       |
 * | **Error**            | [close] with a cause propagates the exception to all subscribers.  |
 * | **Unsubscribe**      | Cancelling a subscriber's collect removes it from the fan-out.     |
 *
 * @param capacity Per-subscriber [Channel] buffer size.
 */
class SubscribableChannelFlow<T>(
    val capacity: Int = Channel.BUFFERED,
) : FlowCollector<T> {

    private val mutex = Mutex()
    private val channels = mutableListOf<Channel<T>>()

    @Volatile
    private var done = false

    @Volatile
    private var doneCause: Throwable? = null

    /**
     * Emits [value] to every currently-registered subscriber channel.
     *
     * The call suspends until every channel has
     * accepted the element (i.e. had buffer space or a waiting consumer).
     *
     * @throws IllegalStateException if the flow is already closed.
     */
    override suspend fun emit(value: T) {
        check(!done) { "emit() called on a closed SubscribableChannelFlow" }
        val snapshot = mutex.withLock { channels.toList() }

        for (ch in snapshot) {
            try {
                ch.send(value)
            } catch (_: ClosedSendChannelException) {
                // Subscriber cancelled between snapshot and send — ignore
            }
        }
    }


    /**
     * Closes the flow, signalling completion (or failure) to all subscribers.
     *
     * Each subscriber's channel is closed; their [Flow] will complete normally
     * (or throw [cause]) after draining any buffered elements.  Calling [close]
     * more than once is a no-op.
     */
    fun close(cause: Throwable? = null) {
        if (done) return
        done = true
        doneCause = cause
        // Safe non-suspending snapshot and close
        val snapshot: List<Channel<T>>
        synchronized(this) {
            snapshot = channels.toList()
            channels.clear()
        }
        snapshot.forEach { it.close(cause) }
    }


    /**
     * Returns a hot [Flow] that receives elements emitted **after** this call.
     *
     * The actual subscription is registered when [Flow.collect] starts —
     * elements emitted before [collect] is called are not buffered and will
     * be missed.
     *
     * The subscriber is automatically unregistered when:
     * - The producer calls [close] (Flow completes / throws).
     * - The collector's coroutine is cancelled.
     */
    suspend fun subscribe(): ClosableFlow<T> {
        // Already closed before we even started collecting
        if (done) {
            doneCause?.let { throw it }
            val emptyFlow = emptyFlow<T>()
            return ClosableFlow(block = emptyFlow::collect, onClose = {})
        }
        val ch = Channel<T>(capacity)
        // Register under lock — check again in case close() raced us
        mutex.withLock {
            if (done) {
                ch.close(doneCause)
            } else {
                channels.add(ch)
            }
        }
        return ClosableFlow(
            block = {
                try {
                    for (value in ch) emit(value)
                    doneCause?.let { throw it }
                } finally {
                    mutex.withLock { channels.remove(ch) }
                }
            },
            onClose = {
                ch.close()
            }
        )
    }

    suspend fun waitForSubscribers(count: Int) {
        withTimeoutOrNull(1.seconds) {
            while (true) {
                val currentCount = mutex.withLock { channels.size }
                if (currentCount >= count) break
                delay(10)
            }
        }
    }
}
