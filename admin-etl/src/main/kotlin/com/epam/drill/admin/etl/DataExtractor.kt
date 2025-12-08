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

import kotlinx.coroutines.flow.FlowCollector
import java.time.Instant

/**
 * DataExtractor defines the read-side contract for the ETL process.
 * Its primary responsibility is to efficiently fetch raw data from a specific data source.
 *
 * Key characteristics:
 * - Owns the **SQL queries** or repository calls used to access raw data.
 * - Respects `fetchSize` to avoid loading the entire dataset at once.
 * - Streams data out as a **sequence / flow of domain objects**.
 * - Is agnostic of:
 *     - how data will be transformed,
 *     - how metrics will be stored.
 */
interface DataExtractor<T> {
    val name: String
    suspend fun extract(
        sinceTimestamp: Instant,
        untilTimestamp: Instant,
        emitter: FlowCollector<T>,
        onExtractCompleted: suspend (EtlExtractingResult) -> Unit
    )
}

