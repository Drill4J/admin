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

import com.epam.drill.admin.etl.EtlWorkerPool
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID

/**
 * The simplest [EtlWorkerPool] implementation: bounds concurrency within a single process using
 * an in-memory [Semaphore] with [maxWorkers] permits.
 */
class SemaphoreWorkerPool(
    private val maxWorkers: Int,
) : EtlWorkerPool {
    private val semaphore = Semaphore(maxWorkers)

    override suspend fun <T> withWorker(block: suspend (workerId: String) -> T): T {
        val workerId = UUID.randomUUID().toString()
        return semaphore.withPermit { block(workerId) }
    }
}
