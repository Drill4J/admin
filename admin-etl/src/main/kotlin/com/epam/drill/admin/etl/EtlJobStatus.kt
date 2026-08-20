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
package com.epam.drill.admin.etl

/**
 * Lifecycle status of a single [EtlJob].
 */
enum class EtlJobStatus {
    /** Worker reached the snapshot time, or ran out of data, but the period hasn't ended yet. */
    IDLE,

    /** Worker is running, or unexpectedly crashed and is no longer updating `lock_expires_at`. */
    RUNNING,

    /**
     * Cancel has been requested.
     */
    CANCELLING,

    /** Worker reached the end of the period without errors and exited. */
    COMPLETED,

    /** Worker encountered an error that made it impossible to continue and exited. */
    ERROR,

    /** Worker was cancelled without finishing the job. */
    CANCELLED;

    /** Statuses that represent a job that is still eligible to be (re)started or resumed. */
    companion object {
        val ACTIVE: Set<EtlJobStatus> = setOf(IDLE, RUNNING, CANCELLING)
    }
}
