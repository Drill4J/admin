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
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubscribableChannelFlowTest {

    @Test
    fun `rendezvous channel cancel resumes pending sender with CancellationException`() = runBlocking {
        val ch = Channel<Int>(Channel.RENDEZVOUS)
        val senderException = CompletableDeferred<Throwable>()

        val sender = launch(Dispatchers.Default) {
            try {
                ch.send(42)
                senderException.completeExceptionally(AssertionError("send should have thrown"))
            } catch (e: Throwable) {
                senderException.complete(e)
            }
        }

        delay(50) // give sender time to suspend on the RENDEZVOUS send
        ch.cancel()  // cancel() unblocks suspended senders; close() would NOT
        sender.join()

        val ex = senderException.await()
        assertTrue(ex is CancellationException, "Expected CancellationException but got: ${ex::class}")
    }

    @Test
    fun `producer does not hang when one subscriber throws in collect`() = runBlocking {
        val flow = SubscribableChannelFlow<Int>(capacity = Channel.RENDEZVOUS)

        val subAReceived = mutableListOf<Int>()

        // subA — healthy subscriber, collects all values
        val subAFlow = flow.subscribe()
        val jobA = launch(Dispatchers.Default) {
            runCatching {
                subAFlow.collect { value -> subAReceived.add(value) }
            }
        }

        // subB — throws immediately on the first received element
        val subBFlow = flow.subscribe()
        val jobB = launch(Dispatchers.Default) {
            runCatching {
                subBFlow.collect { throw RuntimeException("subB intentional error") }
            }
        }

        // Wait until both channels are registered
        flow.waitForSubscribers(2)

        withTimeout(2_000) {
            flow.emit(1) // subA and subB both receive 1; subB throws and exits
            flow.emit(2) // only subA is left; without fix → hangs on chB.send(2)
            flow.emit(3)
            flow.close()
        }

        jobA.join()
        jobB.join()

        // subA must have received all values emitted before close
        assertEquals(listOf(1, 2, 3), subAReceived)
    }

    @Test
    fun `all subscribers receive values in normal case`() = runBlocking {
        val flow = SubscribableChannelFlow<String>()

        val resultsA = mutableListOf<String>()
        val resultsB = mutableListOf<String>()

        val subAFlow = flow.subscribe()
        val subBFlow = flow.subscribe()

        val jobA = launch { subAFlow.collect { resultsA.add(it) } }
        val jobB = launch { subBFlow.collect { resultsB.add(it) } }

        flow.waitForSubscribers(2)

        flow.emit("hello")
        flow.emit("world")
        flow.close()

        jobA.join()
        jobB.join()

        assertEquals(listOf("hello", "world"), resultsA)
        assertEquals(listOf("hello", "world"), resultsB)
    }

    @Test
    fun `close with cause propagates exception to all subscribers`() = runBlocking {
        val flow = SubscribableChannelFlow<Int>()
        val cause = IllegalStateException("producer failed")

        val subFlow = flow.subscribe()
        val caught = CompletableDeferred<Throwable>()

        val job = launch {
            runCatching { subFlow.collect { } }.onFailure { caught.complete(it) }
        }

        flow.waitForSubscribers(1)
        flow.close(cause)

        job.join()

        val error = withTimeout(1_000) { caught.await() }
        assertEquals(cause::class, error::class)
        assertEquals(cause.message, error.message)
    }

    @Test
    fun `subscribe on already-closed flow returns empty flow`() = runBlocking {
        val flow = SubscribableChannelFlow<Int>()
        flow.close()

        val received = mutableListOf<Int>()
        flow.subscribe().collect { received.add(it) }

        assertEquals(emptyList(), received)
    }

    @Test
    fun `subscribe on flow closed with cause throws immediately`(): Unit = runBlocking {
        val flow = SubscribableChannelFlow<Int>()
        val cause = RuntimeException("already failed")
        flow.close(cause)

        assertThrows<RuntimeException> {
            runBlocking { flow.subscribe() }
        }
    }
}







