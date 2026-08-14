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
 * Bounds the number of workers concurrently allowed to work on ETL jobs.
 *
 * Implementations decide how a worker slot is granted (e.g. an in-memory semaphore, a
 * distributed lock/leasing scheme, etc.) so callers only need to run their work inside
 * [withWorker].
 */
interface EtlWorkerPool {

    /**
     * Runs [block] once a worker slot becomes available, releasing the slot back to the pool
     * when [block] completes (successfully or with an exception).
     */
    suspend fun <T> withWorker(block: suspend (workerId: String) -> T): T
}
