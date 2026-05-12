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
package com.epam.drill.admin.writer.rawdata.config

import io.ktor.server.config.ApplicationConfig

/**
 * Configuration for the raw data writer module.
 *
 * @property config The application configuration.
 */
class RawDataConfig(private val config: ApplicationConfig) {
    /**
     * Defines the capacity of the queue used for processing incoming raw data.
     * If the queue reaches its capacity, processing of new data will be suspended until there is space available.
     */
    val queueCapacity: Int
        get() = config.propertyOrNull("queueCapacity")?.getString()?.toIntOrNull() ?: 1000

    /**
     * Defines the number of concurrent workers that will process the raw data from the queue.
     */
    val queueWorkers: Int
        get() = config.propertyOrNull("queueWorkers")?.getString()?.toIntOrNull() ?: 10
}