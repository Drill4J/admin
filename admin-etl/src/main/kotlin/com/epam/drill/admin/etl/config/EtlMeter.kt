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