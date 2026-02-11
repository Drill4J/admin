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

import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * DataLoader` defines the write-side contract for the ETL process.
 * It receives already transformed data (usually metric DTOs or aggregated entities) and persists it to target storage.
 *
 * Key characteristics:
 * - Encapsulates all write logic:
 *     - upserts,
 *     - deletions (if needed).
 * - Uses `batchSize` to control transaction size and DB pressure.
 * - Is resilient to partial failures
 */
interface DataLoader<T: EtlRow> {
    val name: String
    suspend fun load(
        groupId: String,
        sinceTimestamp: Instant,
        untilTimestamp: Instant,
        collector: Flow<T>,
        onLoadingProgress: suspend (EtlLoadingResult) -> Unit = {},
        onStatusChanged: suspend (EtlStatus) -> Unit = {},
    ): EtlLoadingResult

    suspend fun deleteAll(groupId: String)
}