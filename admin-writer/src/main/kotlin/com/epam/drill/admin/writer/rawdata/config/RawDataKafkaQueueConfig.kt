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
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import java.util.Properties

/**
 * Kafka-specific configuration for the raw data queue.
 *
 * Connects to a single Kafka topic acting as an intermediary between the data-ingest endpoint
 * and the raw-data writer.
 */
class RawDataKafkaQueueConfig(private val config: ApplicationConfig) {
    /**
     * Comma-separated list of `host:port` pairs used to establish the initial connection to the
     * Kafka cluster.
     */
    val bootstrapServers: String
        get() = config.string("kafka.bootstrapServers", "localhost:9092")

    /**
     * Name of the Kafka topic to which raw data is produced and from which it is consumed.
     * Auto creation of the topic is disabled by default, so it must be provisioned before starting the service.
     */
    val topic: String
        get() = config.string("kafka.topic", "drill-raw-data")

    /**
     * Consumer group identifier shared by all consumer instances of this service.
     */
    val consumerGroupId: String
        get() = config.string("kafka.consumerGroupId", "drill-writer")

    /**
     * Maximum time in milliseconds the consumer will block waiting for new records in a
     * single [org.apache.kafka.clients.consumer.KafkaConsumer.poll] call.
     */
    val pollTimeoutMs: Long
        get() = config.long("kafka.pollTimeoutMs", 500L)

    /**
     * Maximum time in milliseconds to wait for in-flight records to be flushed and the consumer
     * to commit its offsets during a graceful shutdown.
     * Should be greater than [pollTimeoutMs] to avoid data loss on shutdown.
     */
    val shutdownTimeoutMs: Long
        get() = config.long("kafka.shutdownTimeoutMs", 5_000L)

    /**
     * Builds a [java.util.Properties] map for the Kafka producer.
     */
    val producerProperties: Properties
        get() = Properties().apply {
            putAll(commonClientProperties())
            put(ProducerConfig.CLIENT_ID_CONFIG, config.string("kafka.producer.clientId", "drill-admin-raw-data-producer"))
            put(ProducerConfig.ACKS_CONFIG, config.string("kafka.producer.acks", "all"))
            put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, config.boolean("kafka.producer.enableIdempotence", true).toString())
            put(ProducerConfig.RETRIES_CONFIG, config.int("kafka.producer.retries", Int.MAX_VALUE).toString())
            put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, config.int("kafka.producer.maxInFlightRequestsPerConnection", 5).toString())
            put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, config.int("kafka.producer.deliveryTimeoutMs", 120_000).toString())
            put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, config.int("kafka.producer.requestTimeoutMs", 30_000).toString())
            put(ProducerConfig.LINGER_MS_CONFIG, config.int("kafka.producer.lingerMs", 20).toString())
            put(ProducerConfig.BATCH_SIZE_CONFIG, config.int("kafka.producer.batchSize", 32_768).toString())
            put(ProducerConfig.COMPRESSION_TYPE_CONFIG, config.string("kafka.producer.compressionType", "lz4"))
        }

    /**
     * Builds a [java.util.Properties] map for the Kafka consumer.
     */
    val consumerProperties: Properties
        get() = Properties().apply {
            putAll(commonClientProperties())
            put(ConsumerConfig.CLIENT_ID_CONFIG, config.string("kafka.consumer.clientId", "drill-admin-raw-data-consumer"))
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
            put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, config.boolean("kafka.consumer.allowAutoCreateTopics", false).toString())
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, config.string("kafka.consumer.autoOffsetReset", "earliest"))
            put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, config.string("kafka.consumer.isolationLevel", "read_committed"))
            put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, config.int("kafka.consumer.maxPollRecords", 500).toString())
            put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, config.int("kafka.consumer.fetchMinBytes", 1).toString())
            put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, config.int("kafka.consumer.fetchMaxWaitMs", 500).toString())
            put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, config.int("kafka.consumer.sessionTimeoutMs", 45_000).toString())
            put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, config.int("kafka.consumer.heartbeatIntervalMs", 15_000).toString())
            put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, config.int("kafka.consumer.maxPollIntervalMs", 300_000).toString())
        }

    /**
     * Builds shared [java.util.Properties] for both producer and consumer clients.
     */
    private fun commonClientProperties(): Properties = Properties().apply {
        put(CommonClientConfigs.CLIENT_DNS_LOOKUP_CONFIG, config.string("kafka.clientDnsLookup", "use_all_dns_ips"))
        put(CommonClientConfigs.CONNECTIONS_MAX_IDLE_MS_CONFIG, config.int("kafka.connectionsMaxIdleMs", 540_000).toString())
        put(CommonClientConfigs.RECONNECT_BACKOFF_MS_CONFIG, config.int("kafka.reconnectBackoffMs", 50).toString())
        put(CommonClientConfigs.RECONNECT_BACKOFF_MAX_MS_CONFIG, config.int("kafka.reconnectBackoffMaxMs", 1_000).toString())
        put(CommonClientConfigs.RETRY_BACKOFF_MS_CONFIG, config.int("kafka.retryBackoffMs", 100).toString())
        config.stringOrNull("kafka.securityProtocol")?.let { put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, it) }
        config.stringOrNull("kafka.saslMechanism")?.let { put(SaslConfigs.SASL_MECHANISM, it) }
        config.stringOrNull("kafka.saslJaasConfig")?.let { put(SaslConfigs.SASL_JAAS_CONFIG, it) }
        config.stringOrNull("kafka.sslTruststoreLocation")?.let { put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, it) }
        config.stringOrNull("kafka.sslTruststorePassword")?.let { put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, it) }
        config.stringOrNull("kafka.sslKeystoreLocation")?.let { put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, it) }
        config.stringOrNull("kafka.sslKeystorePassword")?.let { put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, it) }
        config.stringOrNull("kafka.sslKeyPassword")?.let { put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, it) }
        config.stringOrNull("kafka.sslEndpointIdentificationAlgorithm")
            ?.let { put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, it) }
    }
}

private fun ApplicationConfig.string(path: String, default: String): String =
    stringOrNull(path) ?: default

private fun ApplicationConfig.stringOrNull(path: String): String? =
    propertyOrNull(path)?.getString()?.takeIf { it.isNotBlank() }

private fun ApplicationConfig.int(path: String, default: Int): Int =
    propertyOrNull(path)?.getString()?.toIntOrNull() ?: default

private fun ApplicationConfig.long(path: String, default: Long): Long =
    propertyOrNull(path)?.getString()?.toLongOrNull() ?: default

private fun ApplicationConfig.boolean(path: String, default: Boolean): Boolean =
    propertyOrNull(path)?.getString()?.toBooleanStrictOrNull() ?: default
