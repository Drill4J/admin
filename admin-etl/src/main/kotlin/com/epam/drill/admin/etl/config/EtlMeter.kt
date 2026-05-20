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
import java.util.concurrent.atomic.AtomicLong

class EtlMeter(val registry: MeterRegistry) {
    fun registerLongGauge(metricName: String, jobName: String, groupId: String): AtomicLong {
        val value = AtomicLong(0)
        Gauge.builder(metricName) { value.get() }
            .tag("jobName", jobName)
            .tag("groupId", groupId)
            .register(registry)
        return value
    }

    fun registerCounter(metricName: String, jobName: String, groupId: String): Counter {
        return Counter.builder(metricName)
            .tag("jobName", jobName)
            .tag("groupId", groupId)
            .register(registry)
    }


    fun registerTimer(metricName: String, jobName: String, groupId: String): Timer {
        return Timer.builder(metricName)
            .tag("jobName", jobName)
            .tag("groupId", groupId)
            .register(registry)
    }
}