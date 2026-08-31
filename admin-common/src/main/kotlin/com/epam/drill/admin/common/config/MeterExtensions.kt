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
package com.epam.drill.admin.common.config

import io.micrometer.core.instrument.Timer

/**
 * Measures the duration of a suspended block and records it in this [Timer].
 *
 * Unlike [Timer.record], this function correctly handles Kotlin coroutines:
 * it captures wall-clock time across suspension points using [Timer.Sample].
 *
 * Usage:
 * ```kotlin
 * val savingLatency = Timer.builder("metrics.savingLatency")
 *     .description("Latency of saving raw data")
 *     .register(registry)
 *
 * savingLatency.recordSuspend {
 *     someRepository.save(entity)
 * }
 * ```
 */
suspend fun <T> Timer.recordSuspend(block: suspend () -> T): T {
    val sample = Timer.start()
    return try {
        block()
    } finally {
        sample.stop(this)
    }
}

/**
 * Measures the duration of a non-suspended block and records it in this [Timer].
 *
 * This is a convenient wrapper around [Timer.record] that allows using a lambda
 * instead of a [Timer.Sample]. It is suitable for measuring short, non-suspending operations.
 *
 * Usage:
 * ```kotlin
 * val processingLatency = Timer.builder("metrics.processingLatency")
 *     .description("Latency of processing data")
 *     .register(registry)
 *
 * processingLatency.recordInline {
 *     processData(data)
 * }
 * ```
 */
inline fun <T> Timer.recordInline(crossinline block: () -> T): T {
    val sample = Timer.start()
    return try {
        block()
    } finally {
        sample.stop(this)
    }
}

