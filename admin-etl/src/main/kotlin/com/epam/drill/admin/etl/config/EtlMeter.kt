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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class EtlMeter(val registry: MeterRegistry) {

    fun rowsFetched(jobName: String, groupId: String): Counter {
        return registerCounter("etl_rows_fetched", jobName, groupId)
    }

    fun rowsExtracted(jobName: String, groupId: String): Counter {
        return registerCounter("etl_rows_extracted", jobName, groupId)
    }

    fun rowsAggregated(jobName: String, groupId: String): Counter {
        return registerCounter("etl_rows_aggregated", jobName, groupId)
    }

    fun aggregationBufferOccupancyRatio(jobName: String, groupId: String): AtomicReference<Double> {
        return registerDoubleGauge("etl_aggregation_buffer_occupancy_ratio", jobName, groupId)
    }

    fun extractionBufferOccupancyRatio(jobName: String, groupId: String): AtomicInteger {
        return registerIntegerGauge("etl_extraction_buffer_occupancy_ratio", jobName, groupId)
    }

    fun rowsFiltered(jobName: String, groupId: String): Counter {
        return registerCounter("etl_rows_filtered", jobName, groupId)
    }

    fun rowsProcessed(jobName: String, groupId: String): Counter {
        return registerCounter("etl_rows_processed", jobName, groupId)
    }

    fun rowsLoaded(jobName: String, groupId: String): Counter {
        return registerCounter("etl_rows_loaded", jobName, groupId)
    }

    fun rowsSkipped(jobName: String, groupId: String): Counter {
        return registerCounter("etl_rows_skipped", jobName, groupId)
    }

    fun loadingFailures(jobName: String, groupId: String): Counter {
        return registerCounter("etl_loading_failures", jobName, groupId)
    }

    fun extractionFailures(jobName: String, groupId: String): Counter {
        return registerCounter("etl_extraction_failures", jobName, groupId)
    }

    fun loadingDuration(jobName: String, groupId: String): Timer {
        return registerTimer("etl_loading_duration", jobName, groupId)
    }

    fun extractionDuration(jobName: String, groupId: String): Timer {
        return registerTimer("etl_extraction_duration", jobName, groupId)
    }

    private fun registerIntegerGauge(metricName: String, jobName: String, groupId: String): AtomicInteger {
        val value = AtomicInteger(0)
        Gauge.builder(metricName) { value.get() }
            .tag("jobName", jobName)
            .tag("groupId", groupId)
            .register(registry)
        return value
    }

    private fun registerDoubleGauge(metricName: String, jobName: String, groupId: String): AtomicReference<Double> {
        val value = AtomicReference(0.0)
        Gauge.builder(metricName) { value.get() }
            .tag("jobName", jobName)
            .tag("groupId", groupId)
            .register(registry)
        return value
    }

    private fun registerCounter(metricName: String, jobName: String, groupId: String): Counter {
        return Counter.builder(metricName)
            .tag("jobName", jobName)
            .tag("groupId", groupId)
            .register(registry)
    }


    private fun registerTimer(metricName: String, jobName: String, groupId: String): Timer {
        return Timer.builder(metricName)
            .tag("jobName", jobName)
            .tag("groupId", groupId)
            .register(registry)
    }
}