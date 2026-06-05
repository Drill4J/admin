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

import com.epam.drill.admin.etl.EtlContext
import com.epam.drill.admin.etl.impl.toMap
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class EtlMeter(val registry: MeterRegistry) {

    fun rowsFetched(jobName: String, context: EtlContext): Counter {
        return registerCounter("etl_rows_fetched", jobName, context)
    }

    fun rowsExtracted(jobName: String, context: EtlContext): Counter {
        return registerCounter("etl_rows_extracted", jobName, context)
    }

    fun rowsAggregated(jobName: String, context: EtlContext): Counter {
        return registerCounter("etl_rows_aggregated", jobName, context)
    }

    fun aggregationBufferOccupancyRatio(jobName: String, context: EtlContext): AtomicReference<Double> {
        return registerDoubleGauge("etl_aggregation_buffer_occupancy_ratio", jobName, context)
    }

    fun extractionBufferOccupancyRatio(jobName: String, context: EtlContext): AtomicInteger {
        return registerIntegerGauge("etl_extraction_buffer_occupancy_ratio", jobName, context)
    }

    fun rowsFiltered(jobName: String, context: EtlContext): Counter {
        return registerCounter("etl_rows_filtered", jobName, context)
    }

    fun rowsProcessed(jobName: String, context: EtlContext): Counter {
        return registerCounter("etl_rows_processed", jobName, context)
    }

    fun rowsLoaded(jobName: String, context: EtlContext): Counter {
        return registerCounter("etl_rows_loaded", jobName, context)
    }

    fun rowsSkipped(jobName: String, context: EtlContext): Counter {
        return registerCounter("etl_rows_skipped", jobName, context)
    }

    fun loadingFailures(jobName: String, context: EtlContext): Counter {
        return registerCounter("etl_loading_failures", jobName, context)
    }

    fun extractionFailures(jobName: String, context: EtlContext): Counter {
        return registerCounter("etl_extraction_failures", jobName, context)
    }

    fun loadingDuration(jobName: String, context: EtlContext): Timer {
        return registerTimer("etl_loading_duration", jobName, context)
    }

    fun extractionDuration(jobName: String, context: EtlContext): Timer {
        return registerTimer("etl_extraction_duration", jobName, context)
    }

    private fun registerIntegerGauge(metricName: String, jobName: String, context: EtlContext): AtomicInteger {
        val value = AtomicInteger(0)
        Gauge.builder(metricName) { value.get() }
            .tag("jobName", jobName)
            .tagContext(context)
            .register(registry)
        return value
    }

    private fun registerDoubleGauge(metricName: String, jobName: String, context: EtlContext): AtomicReference<Double> {
        val value = AtomicReference(0.0)
        Gauge.builder(metricName) { value.get() }
            .tag("jobName", jobName)
            .tagContext(context)
            .register(registry)
        return value
    }

    private fun registerCounter(metricName: String, jobName: String, context: EtlContext): Counter {
        return Counter.builder(metricName)
            .tag("jobName", jobName)
            .tagContext(context)
            .register(registry)
    }


    private fun registerTimer(metricName: String, jobName: String, context: EtlContext): Timer {
        return Timer.builder(metricName)
            .tag("jobName", jobName)
            .tagContext(context)
            .register(registry)
    }

    private fun tagContext(context: EtlContext, tag: (String, String) -> Unit) {
        context.toMap()
            .mapNotNull { (k, v) -> k to v.toString() }
            .forEach { (k, v) -> tag(k, v) }
    }
    private fun <T> Gauge.Builder<T>.tagContext(context: EtlContext) = tagContext(context, this::tag).let { this }
    private fun Counter.Builder.tagContext(context: EtlContext) = tagContext(context, this::tag).let { this }
    private fun Timer.Builder.tagContext(context: EtlContext) = tagContext(context, this::tag).let { this }
}