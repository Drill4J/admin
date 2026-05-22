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
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import java.util.Properties

/**
 * Common Kafka cluster and connection configuration shared by all Kafka clients.
 */
class KafkaConfig(private val config: ApplicationConfig) {

    /**
     * Comma-separated list of `host:port` pairs used to establish the initial connection to the
     * Kafka cluster.
     */
    val bootstrapServers: String
        get() = config.string("bootstrapServers", "localhost:9092")

    /**
     * Builds a [Properties] map containing connection settings shared by producer and consumer
     * clients (DNS lookup, idle/backoff timeouts, and security/SSL properties).
     */
    fun toCommonProperties(): Properties = Properties().apply {
        put(CommonClientConfigs.CLIENT_DNS_LOOKUP_CONFIG, config.string("clientDnsLookup", "use_all_dns_ips"))
        put(CommonClientConfigs.CONNECTIONS_MAX_IDLE_MS_CONFIG, config.int("connectionsMaxIdleMs", 540_000).toString())
        put(CommonClientConfigs.RECONNECT_BACKOFF_MS_CONFIG, config.int("reconnectBackoffMs", 50).toString())
        put(CommonClientConfigs.RECONNECT_BACKOFF_MAX_MS_CONFIG, config.int("reconnectBackoffMaxMs", 1_000).toString())
        put(CommonClientConfigs.RETRY_BACKOFF_MS_CONFIG, config.int("retryBackoffMs", 100).toString())
        config.stringOrNull("securityProtocol")?.let { put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, it) }
        config.stringOrNull("saslMechanism")?.let { put(SaslConfigs.SASL_MECHANISM, it) }
        config.stringOrNull("saslJaasConfig")?.let { put(SaslConfigs.SASL_JAAS_CONFIG, it) }
        config.stringOrNull("sslTruststoreLocation")?.let { put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, it) }
        config.stringOrNull("sslTruststorePassword")?.let { put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, it) }
        config.stringOrNull("sslKeystoreLocation")?.let { put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, it) }
        config.stringOrNull("sslKeystorePassword")?.let { put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, it) }
        config.stringOrNull("sslKeyPassword")?.let { put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, it) }
        config.stringOrNull("sslEndpointIdentificationAlgorithm")
            ?.let { put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, it) }
    }
}

internal fun ApplicationConfig.string(path: String, default: String): String =
    stringOrNull(path) ?: default

internal fun ApplicationConfig.stringOrNull(path: String): String? =
    propertyOrNull(path)?.getString()?.takeIf { it.isNotBlank() }

internal fun ApplicationConfig.int(path: String, default: Int): Int =
    propertyOrNull(path)?.getString()?.toIntOrNull() ?: default

internal fun ApplicationConfig.long(path: String, default: Long): Long =
    propertyOrNull(path)?.getString()?.toLongOrNull() ?: default

internal fun ApplicationConfig.boolean(path: String, default: Boolean): Boolean =
    propertyOrNull(path)?.getString()?.toBooleanStrictOrNull() ?: default

