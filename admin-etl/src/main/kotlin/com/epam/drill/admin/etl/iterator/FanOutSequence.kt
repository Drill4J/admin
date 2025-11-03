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
package com.epam.drill.admin.etl.iterator

import java.util.concurrent.atomic.AtomicInteger

class FanOutSequence<T>(
    private val delegate: Iterator<T>
) : Sequence<T> {
    private val buffer = ArrayDeque<T>()
    private var completed = false
    private val lock = Any()
    private val indexes = mutableSetOf<AtomicInteger>()

    override fun iterator(): Iterator<T> = object : Iterator<T> {
        var index = AtomicInteger(0).also { indexes.add(it) }

        override fun hasNext(): Boolean {
            synchronized(lock) {
                if (index.get() < buffer.size) return true
                if (completed) return false
                if (delegate.hasNext()) {
                    buffer += delegate.next()
                    return true
                } else {
                    completed = true
                    return false
                }
            }
        }

        override fun next(): T {
            synchronized(lock) {
                if (!hasNext())
                    throw NoSuchElementException()
                val value = buffer[index.getAndAdd(1)]
                if (indexes.minOf { it.get() } == index.get()) {
                    // Clean up buffer to free memory
                    buffer.removeFirst()
                    indexes.forEach { it.decrementAndGet() }
                }
                return value
            }
        }
    }
}