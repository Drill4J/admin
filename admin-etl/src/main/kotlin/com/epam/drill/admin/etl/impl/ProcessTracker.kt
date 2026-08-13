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
package com.epam.drill.admin.etl.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

class ProgressTracker<T>(val job: suspend () -> T) {
    suspend fun every(duration: Duration, track: suspend CoroutineScope.() -> Unit): T = coroutineScope {
        val trackingJob = launch {
            while (isActive) {
                delay(duration.inWholeMilliseconds)
                this@coroutineScope.track()
            }
        }
        try {
            return@coroutineScope job()
        } finally {
            trackingJob.cancelAndJoin()
        }
    }
}

fun <T> trackProgressOf(job: suspend () -> T) = ProgressTracker(job)