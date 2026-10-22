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
import io.micrometer.core.instrument.LongTaskTimer
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class EtlMeter(val registry: MeterRegistry) {

    // Micrometer silently ignores a Gauge registration when a meter with the same name and tags
    // already exists, keeping the value supplier from the first registration. Since jobs are
    // re-run repeatedly with identical tags (jobName/context), we must reuse the same backing
    // AtomicInteger/AtomicReference across calls instead of creating a fresh one each time,
    // otherwise later runs would update a value that the registered gauge never reads.
    private val gauges = ConcurrentHashMap<String, MutableList<GaugeSample>>()

    fun rowsFetched(jobName: String, context: EtlContext): Counter {
        return registerCounter("etl_rows_fetched", jobName, context)
    }

    fun rowsExtracted(jobName: String, context: EtlContext): Counter {
        return registerCounter("etl_rows_extracted", jobName, context)
    }

    fun rowsAggregated(jobName: String, context: EtlContext): Counter {
        return registerCounter("etl_rows_aggregated", jobName, context)
    }

    fun aggregationBufferOccupancyRatio(jobName: String, context: EtlContext): GaugeSample {
        return registerDoubleGauge("etl_aggregation_buffer_occupancy_ratio", jobName, context)
    }

    fun buildProbeLayoutBufferOccupancyRatio(jobName: String, context: EtlContext): GaugeSample {
        return registerDoubleGauge("etl_build_probe_layout_buffer_occupancy_ratio", jobName, context)
    }

    fun buildProbeLayoutLoadingDuration(jobName: String, context: EtlContext): Timer {
        return registerTimer("etl_build_probe_layout_loading_duration", jobName, context)
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

    fun etlDuration(jobName: String, context: EtlContext): LongTaskTimer {
        return LongTaskTimer.builder("etl_duration")
            .tag("jobName", jobName)
            .tagContext(context)
            .register(registry)
    }


    private fun registerDoubleGauge(metricName: String, jobName: String, context: EtlContext): GaugeSample {
        val key = gaugeKey(metricName, jobName, context)
        val sample = GaugeSample(AtomicReference(0.0))
        gauges.compute(key) { _, list ->
            if (list != null) {
                list.removeIf { !it.isActive() }
                list.add(sample)
                list
            } else {
                val newList = mutableListOf(sample)
                Gauge.builder(metricName) { newList.maxOfOrNull { it.get() } ?: 0.0 }
                    .tag("jobName", jobName)
                    .tagContext(context)
                    .register(registry)
                newList
            }
        }
        return sample
    }

    private fun gaugeKey(metricName: String, jobName: String, context: EtlContext): String {
        val tags = (context.toMap().mapValues { (_, v) -> v?.toString() ?: "" } + ("jobName" to jobName))
            .toSortedMap()
            .entries
            .joinToString(",") { (k, v) -> "$k=$v" }
        return "$metricName{$tags}"
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
        tag("groupId", context.groupId)
    }

    private fun <T> Gauge.Builder<T>.tagContext(context: EtlContext) = tagContext(context, this::tag).let { this }
    private fun Counter.Builder.tagContext(context: EtlContext) = tagContext(context, this::tag).let { this }
    private fun Timer.Builder.tagContext(context: EtlContext) = tagContext(context, this::tag).let { this }
    private fun LongTaskTimer.Builder.tagContext(context: EtlContext) = tagContext(context, this::tag).let { this }

    class GaugeSample(
        private val gauge: AtomicReference<Double>
    ): AutoCloseable {
        private var active: Boolean = true

        fun get(): Double = gauge.get()

        fun set(value: Double) {
            gauge.set(value)
        }

        fun isActive(): Boolean = active

        override fun close() {
            gauge.set(0.0)
            active = false
        }
    }
}