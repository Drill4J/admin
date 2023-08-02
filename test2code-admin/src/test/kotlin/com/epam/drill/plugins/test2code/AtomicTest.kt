package com.epam.drill.plugins.test2code

import io.ktor.util.collections.*
import org.junit.jupiter.api.Test
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val random = Random()

interface AtomicMap<T> {
    fun hold(key: String, default: () -> T): T
    fun release(key: String): T
    fun getDepth(key: String): Int
    fun getReleased(): List<T>
}

class AtomicMapImpl1<T> : AtomicMap<T> {
    private val counterMap = ConcurrentHashMap<String, AtomicInteger>()
    private val dataMap = ConcurrentHashMap<String, T>()
    private val released = ConcurrentList<T>()

    override fun hold(key: String, default: () -> T): T {
        if (counterMap[key] != null) {
            delayMs(0, 4)
            counterMap[key]!!.incrementAndGet()
        }
        delayMs(0, 4)
        counterMap.getOrPut(key) { AtomicInteger(1) }
        delayMs(0, 4)
        return dataMap.getOrPut(key, default)
    }

    override fun release(key: String): T {
        val value = dataMap[key]!!
        if (counterMap[key] != null) {
            val result = counterMap[key]!!.decrementAndGet()
            if (result == 0) {
                delayMs(0, 4)
                if (dataMap.remove(key) != null) {
                    delayMs(0, 4)
                    released.add(value)
                }
                counterMap.remove(key)
            }
        }
        return value
    }

    override fun getDepth(key: String): Int = counterMap[key]?.get() ?: 0

    override fun getReleased(): List<T> {
        return released
    }
}

class AtomicMapImpl2<T> : AtomicMap<T> {
    private val counterMap = ConcurrentHashMap<String, AtomicInteger>()
    private val dataMap = ConcurrentHashMap<String, T>()
    private val released = ConcurrentList<T>()

    override fun hold(key: String, default: () -> T): T {
        counterMap.computeIfPresent(key) { _, counter ->
            delayMs(0, 4)
            counter.incrementAndGet()
            counter
        }
        delayMs(0, 4)
        counterMap.getOrPut(key) { AtomicInteger(1) }
        delayMs(0, 4)
        return dataMap.getOrPut(key, default)
    }

    override fun release(key: String): T {
        val value = dataMap[key]!!
        delayMs(0, 4)
        counterMap.computeIfPresent(key) { _, counter ->
            val result = counter.decrementAndGet()
            if (result == 0) {
                delayMs(0, 4)
                if (dataMap.remove(key) != null) {
                    delayMs(0, 4)
                    released.add(value)
                }
            }
            counter
        }
        return value
    }

    override fun getDepth(key: String): Int = counterMap[key]?.get() ?: 0

    override fun getReleased(): List<T> {
        return released
    }
}

class SomeObj(val id: Int)

class AtomicMapTest {

    @Test
    fun `10 keys in 1000 threads`() {
        val tester = Tester(
            numberOfThreads = 1000,
            testKeys = 10,
            testDelay = 0 to 0,
            threadsDelay = 0 to 0
        )
        tester.executeTests(AtomicMapImpl2())
    }

    @Test
    fun `2 keys in 1000 threads with 2ms delay`() {
        val tester = Tester(
            numberOfThreads = 1000,
            testKeys = 2,
            testDelay = 0 to 2,
            threadsDelay = 0 to 2
        )
        tester.executeTests(AtomicMapImpl2())
    }
}

class Tester(
    private val numberOfThreads: Int = 1000,
    private val testKeys: Int = 1,
    private val testDelay: Pair<Int, Int> = 0 to 0,
    private val threadsDelay: Pair<Int, Int> = 0 to 0,
    private val assertion: (AtomicMap<SomeObj>) -> Boolean = { counter -> hasNoDuplicateReferences(counter.getReleased()) }
) {
    fun executeTests(impl: AtomicMap<SomeObj>) {
        val threads = mutableListOf<Thread>()
        val finishedThreads = AtomicInteger(0)

        repeat(numberOfThreads) {
            val thread = Thread {
                val id = if (testKeys > 1) random.nextInt() % testKeys else 0
                val key = "test$id"

                impl.hold(key) { SomeObj(it) }

                delayMs(testDelay.first, testDelay.second)

                if (it % 100 == 0)
                    println("Iteration=$it, Key=$key Depth=${impl.getDepth(key)}")

                impl.release(key)

                finishedThreads.incrementAndGet()
            }
            threads.add(thread)
            delayMs(threadsDelay.first, threadsDelay.second)
            thread.start()
        }
        threads.forEach { it.join() }
        assertEquals(finishedThreads.get(), numberOfThreads, "Expected that all threads finished successfully")

        println("Released size = ${impl.getReleased().size}")
        assertTrue { assertion(impl) }
    }
}

fun hasNoDuplicateReferences(list: List<*>): Boolean {
    val uniqueReferences = mutableSetOf<Any>()
    for (item in list) {
        if (!uniqueReferences.add(item!!)) {
            return false
        }
    }
    return true
}

fun delayMs(millisecondsToWait: Int) {
    if (millisecondsToWait > 0)
        Thread.sleep(millisecondsToWait.toLong())
}

fun delayMs(from: Int, until: Int) {
    var millisecondsToWait = until
    if (until - from > 0)
        millisecondsToWait = random.nextInt((until - from) + 1) + from;
    delayMs(millisecondsToWait)
}