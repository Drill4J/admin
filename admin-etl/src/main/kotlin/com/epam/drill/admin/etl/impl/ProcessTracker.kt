package com.epam.drill.admin.etl.impl

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration

class ProgressTracker(val job: suspend () -> Unit) {
    suspend fun every(duration: Duration, track: () -> Unit) = coroutineScope {
        val trackingJob = launch {
            while (isActive) {
                delay(duration.inWholeMilliseconds)
                track()
            }
        }
        try {
            job()
        } finally {
            trackingJob.cancel()
        }
    }
}

fun trackProgressOf(job: suspend () -> Unit) = ProgressTracker(job)