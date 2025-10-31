package com.epam.drill.admin.etl.interator

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