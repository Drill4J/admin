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
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import java.util.Properties

/**
 * Raw-data-queue-specific Kafka configuration.
 *
 * Contains settings that are private to the raw-data processing task: topic name,
 * consumer group, poll/shutdown timeouts, and producer/consumer tuning parameters.
 * Cluster-level connection settings (bootstrap servers, security, SSL) are provided
 * by the shared [KafkaConfig].
 *
 * @param config Application config scoped to `drill.rawData.queue.kafka`.
 * @param kafkaConfig Shared Kafka cluster and connection configuration.
 */
class RawDataKafkaQueueConfig(
    private val config: ApplicationConfig,
    private val kafkaConfig: KafkaConfig,
) {
    /**
     * Name of the Kafka topic to which raw data is produced and from which it is consumed.
     * Auto creation of the topic is disabled by default, so it must be provisioned before starting the service.
     */
    val topic: String
        get() = config.string("topic", "drill-raw-data")

    /**
     * Consumer group identifier shared by all consumer instances of this service.
     */
    val consumerGroupId: String
        get() = config.string("consumerGroupId", "drill-writer")

    /**
     * Maximum time in milliseconds the consumer will block waiting for new records in a
     * single [org.apache.kafka.clients.consumer.KafkaConsumer.poll] call.
     */
    val pollTimeoutMs: Long
        get() = config.long("pollTimeoutMs", 500L)

    /**
     * Maximum time in milliseconds to wait for in-flight records to be flushed and the consumer
     * to commit its offsets during a graceful shutdown.
     * Should be greater than [pollTimeoutMs] to avoid data loss on shutdown.
     */
    val shutdownTimeoutMs: Long
        get() = config.long("shutdownTimeoutMs", 5_000L)

    /**
     * Builds a [java.util.Properties] map for the Kafka producer.
     * Common connection properties are contributed by [kafkaConfig].
     */
    val producerProperties: Properties
        get() = Properties().apply {
            putAll(kafkaConfig.toCommonProperties())
            put(ProducerConfig.CLIENT_ID_CONFIG, config.string("producer.clientId", "drill-admin-raw-data-producer"))
            put(ProducerConfig.ACKS_CONFIG, config.string("producer.acks", "all"))
            put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, config.boolean("producer.enableIdempotence", true).toString())
            put(ProducerConfig.RETRIES_CONFIG, config.int("producer.retries", Int.MAX_VALUE).toString())
            put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, config.int("producer.maxInFlightRequestsPerConnection", 5).toString())
            put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, config.int("producer.deliveryTimeoutMs", 120_000).toString())
            put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, config.int("producer.requestTimeoutMs", 30_000).toString())
            put(ProducerConfig.LINGER_MS_CONFIG, config.int("producer.lingerMs", 20).toString())
            put(ProducerConfig.BATCH_SIZE_CONFIG, config.int("producer.batchSize", 32_768).toString())
            put(ProducerConfig.COMPRESSION_TYPE_CONFIG, config.string("producer.compressionType", "lz4"))
        }

    /**
     * Builds a [java.util.Properties] map for the Kafka consumer.
     * Common connection properties are contributed by [kafkaConfig].
     */
    val consumerProperties: Properties
        get() = Properties().apply {
            putAll(kafkaConfig.toCommonProperties())
            put(ConsumerConfig.CLIENT_ID_CONFIG, config.string("consumer.clientId", "drill-admin-raw-data-consumer"))
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
            put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, config.boolean("consumer.allowAutoCreateTopics", false).toString())
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, config.string("consumer.autoOffsetReset", "earliest"))
            put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, config.string("consumer.isolationLevel", "read_committed"))
            put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, config.int("consumer.maxPollRecords", 500).toString())
            put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, config.int("consumer.fetchMinBytes", 1).toString())
            put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, config.int("consumer.fetchMaxWaitMs", 500).toString())
            put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, config.int("consumer.sessionTimeoutMs", 45_000).toString())
            put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, config.int("consumer.heartbeatIntervalMs", 15_000).toString())
            put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, config.int("consumer.maxPollIntervalMs", 300_000).toString())
        }
}
