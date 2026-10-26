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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class EtlMeter(val registry: MeterRegistry) {

    // Micrometer silently ignores a Gauge registration when a meter with the same name and tags
    // already exists, keeping the value supplier from the first registration. Since jobs are
    // re-run repeatedly with identical tags (jobName/context), we must reuse the same backing
    // AtomicInteger/AtomicReference across calls instead of creating a fresh one each time,
    // otherwise later runs would update a value that the registered gauge never reads.
    private val gauges = ConcurrentHashMap<String, MutableList<GaugeSample>>()

    /**
     * Rows fetched from the source Database in the extraction phase, including query execution and result set processing.
     */
    fun rowsFetched(jobName: String, context: EtlContext): Counter {
        return registerCounter("etl_rows_fetched", jobName, context)
    }

    /**
     * Rows replicated for each pipeline within the extractor group.
     */
    fun rowsExtracted(jobName: String, context: EtlContext): Counter {
        return registerCounter("etl_rows_extracted", jobName, context)
    }

    /**
     * Rows aggregated in the transformation phase.
     */
    fun rowsAggregated(jobName: String, context: EtlContext): Counter {
        return registerCounter("etl_rows_aggregated", jobName, context)
    }

    /**
     * Ratio of the current size of the buffer used for aggregations in the transformation phase to its maximum size.
     */
    fun aggregationBufferUtilizationRatio(jobName: String, context: EtlContext): GaugeSample {
        return registerDoubleGauge("etl_aggregation_buffer_utilization_ratio", jobName, context) {
            it.maxOrNull() ?: 0.0
        }
    }

    /**
     * Ratio of the current size of the cache used for data enrichment in the transformation phase to its maximum size.
     */
    fun lookupCacheUtilizationRatio(jobName: String, context: EtlContext): GaugeSample {
        return registerDoubleGauge("etl_lookup_cache_utilization_ratio", jobName, context) {
            it.maxOrNull() ?: 0.0
        }
    }

    /**
     * Cache misses used for data enrichment during the transformation phase.
     */
    fun lookupCacheMisses(jobName: String, context: EtlContext): Counter {
        return registerCounter("etl_lookup_cache_misses", jobName, context)
    }

    /**
     * Rows filtered out in the transformation phase.
     */
    fun rowsFiltered(jobName: String, context: EtlContext): Counter {
        return registerCounter("etl_rows_filtered", jobName, context)
    }

    /**
     * Rows processed during the transformation phase and available for loading.
     */
    fun rowsProcessed(jobName: String, context: EtlContext): Counter {
        return registerCounter("etl_rows_processed", jobName, context)
    }

    /**
     * Rows successfully loaded into the target Database in the loading phase.
     */
    fun rowsLoaded(jobName: String, context: EtlContext): Counter {
        return registerCounter("etl_rows_loaded", jobName, context)
    }

    /**
     * Rows skipped before the transformation phase due to errors or duplicates.
     */
    fun rowsSkipped(jobName: String, context: EtlContext): Counter {
        return registerCounter("etl_rows_skipped", jobName, context)
    }

    /**
     * ETL jobs failed on the loading phase due to errors.
     */
    fun loadingFailures(jobName: String, context: EtlContext): Counter {
        return registerCounter("etl_loading_failures", jobName, context)
    }

    /**
     * ETL jobs failed on the extraction phase due to errors.
     */
    fun extractionFailures(jobName: String, context: EtlContext): Counter {
        return registerCounter("etl_extraction_failures", jobName, context)
    }

    /**
     * Duration of the loading phase.
     */
    fun loadingDuration(jobName: String, context: EtlContext): Timer {
        return registerTimer("etl_loading_duration", jobName, context)
    }

    /**
     * Duration of the extraction phase.
     */
    fun extractionDuration(jobName: String, context: EtlContext): Timer {
        return registerTimer("etl_extraction_duration", jobName, context)
    }

    /**
     * Duration of the entire ETL job, including all pipelines.
     */
    fun jobDuration(jobName: String, context: EtlContext): LongTaskTimer {
        return LongTaskTimer.builder("etl_job_duration")
            .tag("jobName", jobName)
            .tagContext(context)
            .register(registry)
    }

    /**
     * Duration of the transformation phase.
     */
    fun transformationDuration(jobName: String, context: EtlContext): Timer {
        return registerTimer("etl_transformation_duration", jobName, context)
    }

    /**
     * ETL jobs failed due to errors.
     */
    fun jobsFailed(jobName: String, context: EtlContext): Counter {
        return registerCounter("etl_jobs_failed", jobName, context)
    }

    /**
     * ETL jobs finished successfully.
     */
    fun jobsCompleted(jobName: String, context: EtlContext): Counter {
        return registerCounter("etl_jobs_completed", jobName, context)
    }


    private fun registerDoubleGauge(metricName: String, jobName: String, context: EtlContext, aggregation: (List<Double>) -> Double): GaugeSample {
        val key = gaugeKey(metricName, jobName, context)
        val sample = GaugeSample(AtomicReference(0.0))
        gauges.compute(key) { _, list ->
            if (list != null) {
                list.removeIf { !it.isActive() }
                list.add(sample)
                list
            } else {
                val newList = mutableListOf(sample)
                Gauge.builder(metricName) { aggregation(newList.map { it.get() }) }
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
        private var active = AtomicBoolean(true)

        fun get(): Double = gauge.get()

        fun set(value: Double) {
            gauge.set(value)
        }

        fun isActive(): Boolean = active.get()

        override fun close() {
            gauge.set(0.0)
            active.set(false)
        }
    }
}