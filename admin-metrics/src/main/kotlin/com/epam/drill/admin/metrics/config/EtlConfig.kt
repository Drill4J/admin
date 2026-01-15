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
package com.epam.drill.admin.metrics.config

import io.ktor.server.config.ApplicationConfig

/**
 * Configuration parameters for ETL processing.
 */
class EtlConfig(private val config: ApplicationConfig) {
    /**
     * Controls the in-memory buffer capacity used for the shared flow between the extractor and loaders.
     */
    val bufferSize : Int
        get() = config.propertyOrNull("bufferSize")?.getString()?.toIntOrNull() ?: 2000

    /**
     * Hints the JDBC driver how many rows to fetch from the database per round trip for the SQL extractor.
     */
    val fetchSize : Int
        get() = config.propertyOrNull("fetchSize")?.getString()?.toIntOrNull() ?: 2000

    /**
     * Controls how many items are grouped into a single write batch/transaction for loaders.
     */
    val batchSize : Int
        get() = config.propertyOrNull("batchSize")?.getString()?.toIntOrNull() ?: 1000

    /**
     * Sets a limit on the total number of records to be extracted by the ETL process in a single query.
     */
    val extractionLimit : Int
        get() = config.propertyOrNull("extractionLimit")?.getString()?.toIntOrNull() ?: 1_000_000
}