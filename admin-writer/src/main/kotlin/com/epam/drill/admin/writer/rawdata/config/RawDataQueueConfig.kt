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
 * Configuration for the raw data queue.
 *
 * @param config Application config scoped to `drill.rawData.queue`.
 * @param kafkaConfig Shared Kafka cluster and connection configuration from `drill.kafka`.
 */
class RawDataQueueConfig(
    private val config: ApplicationConfig,
    private val kafkaConfig: KafkaConfig,
) {
    /**
     * Defines the raw data queue implementation.
     */
    val type: RawDataQueueType
        get() = config.propertyOrNull("type")?.getString()?.let { RawDataQueueType.valueOf(it) } ?: RawDataQueueType.IN_MEMORY

    /**
     * Defines the capacity of the queue used for processing incoming raw data.
     * If the queue reaches its capacity, processing of new data will be suspended until there is space available.
     */
    val capacity: Int
        get() = config.propertyOrNull("capacity")?.getString()?.toIntOrNull() ?: 1000

    /**
     * Defines the number of concurrent workers that will process the raw data from the queue.
     * Should be less than database connection pool size.
     */
    val workers: Int
        get() = config.propertyOrNull("workers")?.getString()?.toIntOrNull() ?: 10

    /**
     * Kafka-specific queue configuration (topic, consumer group, poll/shutdown timeouts,
     * producer/consumer tuning). Cluster connection settings are delegated to [kafkaConfig].
     */
    val kafka: RawDataKafkaQueueConfig
        get() = RawDataKafkaQueueConfig(config.config("kafka"), kafkaConfig)
}

enum class RawDataQueueType {
    IN_MEMORY,
    KAFKA
}
