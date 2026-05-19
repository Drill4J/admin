package com.epam.drill.admin.etl.config

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class EtlMeter(val registry: MeterRegistry) {
    private val longGauges = ConcurrentHashMap<String, AtomicLong>()
    private val counters = ConcurrentHashMap<String, Counter>()
    private val timers = ConcurrentHashMap<String, Timer>()

    fun registerLongGauge(metricName: String, jobName: String, groupId: String): AtomicLong {
        val key = "$metricName:$jobName:$groupId"
        return longGauges.compute(key) { _, oldValue ->
            oldValue?.also { it.set(0) } ?: AtomicLong(0).also { newValue ->
                Gauge.builder(metricName) {
                    newValue.get().toDouble()
                }.tag("jobName", jobName)
                    .tag("groupId", groupId)
                    .register(registry)
            }
        } ?: throw IllegalStateException("Failed to create gauge for $key")
    }

    fun registerCounter(metricName: String, jobName: String, groupId: String): Counter {
        val key = "$metricName:$jobName:$groupId"
        return counters.computeIfAbsent(key) { key ->
            Counter.builder(metricName)
                .tag("jobName", jobName)
                .tag("groupId", groupId)
                .register(registry)
        }
    }

    fun registerTimer(metricName: String, jobName: String, groupId: String): Timer {
        val key = "$metricName:$jobName:$groupId"
        return timers.computeIfAbsent(key) {
            Timer.builder(metricName)
                .tag("jobName", jobName)
                .tag("groupId", groupId)
                .register(registry)
        }
    }
}

@Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
inline fun <T> Timer.recordDuration(crossinline block: () -> T): T {
    return this.recordCallable {
        block()
    }
}