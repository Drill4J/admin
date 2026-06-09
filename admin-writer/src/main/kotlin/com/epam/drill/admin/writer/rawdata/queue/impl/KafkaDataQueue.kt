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
package com.epam.drill.admin.writer.rawdata.queue.impl

import com.epam.drill.admin.writer.rawdata.queue.DataQueue
import com.epam.drill.admin.writer.rawdata.queue.QueueInput
import com.epam.drill.admin.writer.rawdata.queue.QueueOutput
import com.epam.drill.admin.writer.rawdata.route.DataIngestRoute
import com.epam.drill.admin.writer.rawdata.route.payload.RawDataPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.withTimeout
import mu.KotlinLogging
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.WakeupException
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.coroutineContext
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import java.time.Duration as JavaDuration

/**
 * Kafka-backed implementation of [DataQueue].
 *
 * @param producer pre-configured Kafka [Producer]; closed by [close].
 * @param consumer pre-configured Kafka [Consumer]; subscribed to [topic] and closed by [close].
 * @param topic single Kafka topic used for both publishing and consuming raw payloads.
 * @param deserializer converts a route + payload bytes into a typed [RawDataPayload].
 * @param recordKeyToPayloadType maps the route key (extracted from the record header) back to the corresponding payload type for deserialization.
 * @param routeToRecordKey extracts the key for a given route instance (defaults to its class simple name).
 * @param RECORD_KEY_HEADER Kafka header name used to carry the route key.
 * @param capacity capacity of the internal channel exposed to consumers of this queue.
 * @param pollTimeout per-poll timeout used by the consumer loop.
 * @param shutdownTimeout maximum time to wait for the background coroutines to finish on [close].
 */
class KafkaDataQueue<R : DataIngestRoute, T : RawDataPayload>(
    private val producer: Producer<String, ByteArray>,
    private val consumer: Consumer<String, ByteArray>,
    private val topic: String,
    private val deserializer: suspend (KClass<out T>, ByteArray) -> T,
    private val recordKeyToPayloadType: (String) -> KClass<out T>,
    private val routeToRecordKey: (R) -> String,
    capacity: Int = Channel.BUFFERED,
    private val pollTimeout: Duration = 500.milliseconds,
    private val shutdownTimeout: Duration = 5.seconds,
) : DataQueue<R, T>, Channel<QueueOutput<T>> by Channel(capacity), AutoCloseable {

    private val logger = KotlinLogging.logger {}
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val outputChannel: SendChannel<QueueOutput<T>> get() = this

    init {
        consumer.subscribe(listOf(topic))
        scope.launch { runConsumerLoop() }
    }

    override suspend fun enqueue(input: QueueInput<R>) {
        val recordKey = routeToRecordKey(input.route)
        val record = ProducerRecord(topic, null, recordKey, input.payload).apply {
            headers().add(RecordHeader(RECORD_KEY_HEADER, recordKey.toByteArray(Charsets.UTF_8)))
            input.metadata.forEach { (k, v) ->
                headers().add(RecordHeader(k, v.toByteArray(Charsets.UTF_8)))
            }
        }
        // Bridge the Kafka producer's java callback to a coroutine.
        val future = CompletableFuture<Unit>()
        producer.send(record) { _, e ->
            if (e != null) future.completeExceptionally(e) else future.complete(Unit)
        }
        future.await()
    }

    override suspend fun dequeue(): QueueOutput<T> = this.receive()

    override fun close() {
        runCatching { consumer.wakeup() }
        runBlocking {
            withTimeout(shutdownTimeout.toJavaDuration()) {
                scope.coroutineContext[Job]?.children?.forEach { it.join() }
            }
            scope.cancel()
        }
        runCatching { consumer.close(shutdownTimeout.toJavaDuration()) }
        runCatching { producer.close(shutdownTimeout.toJavaDuration()) }
        outputChannel.close()
    }

    private suspend fun runConsumerLoop() {
        val pollDuration: JavaDuration = pollTimeout.toJavaDuration()
        try {
            while (coroutineContext[Job]?.isActive == true) {
                val records = try {
                    consumer.poll(pollDuration)
                } catch (e: WakeupException) {
                    throw e
                } catch (e: Throwable) {
                    logger.error(e) { "Error while polling Kafka topic [$topic]: ${e.message}" }
                    null
                } ?: continue

                for (record in records) {
                    val key = record.key()
                    if (key == null) {
                        logger.warn { "Skipping Kafka record without record key on topic [$topic]" }
                        continue
                    }

                    val payloadType = runCatching {
                        recordKeyToPayloadType(key)
                    }.onFailure { e ->
                        logger.error(e) { "Error while determining payload type for [$key]: ${e.message}" }
                    }.getOrNull() ?: continue

                    val payload = runCatching {
                        deserializer(payloadType, record.value())
                    }.onFailure { e ->
                        logger.error(e) { "Error while deserializing record for [$key]: ${e.message}" }
                    }.getOrNull() ?: continue

                    val metadata = record.headers()
                        .associate { it.key() to it.value().toString(Charsets.UTF_8) }

                    this@KafkaDataQueue.send(QueueOutput(payload, metadata))
                }

                runCatching {
                    consumer.commitAsync()
                }.onFailure { e ->
                    logger.warn(e) { "Kafka commitAsync failed: ${e.message}" }
                }
            }
        } catch (_: WakeupException) {
            logger.debug { "Kafka consumer woken up, exiting poll loop" }
        } catch (e: Throwable) {
            logger.error(e) { "Kafka consumer loop terminated unexpectedly: ${e.message}" }
        }
    }

    companion object {
        const val RECORD_KEY_HEADER = "drill-record-key"

        /**
         * Convenience factory that builds Kafka producer/consumer from raw [Properties].
         * Key/value (de)serializers are forced to String/ByteArray.
         */
        fun <R : DataIngestRoute, T : RawDataPayload> create(
            bootstrapServers: String,
            topic: String,
            consumerGroupId: String,
            deserializer: suspend (KClass<out T>, ByteArray) -> T,
            recordKeyToPayloadType: (String) -> KClass<out T>,
            routeToRecordKey: (R) -> String,
            producerProps: Properties = Properties(),
            consumerProps: Properties = Properties(),
            capacity: Int = Channel.BUFFERED,
            pollTimeout: Duration = 500.milliseconds,
            shutdownTimeout: Duration = 5.seconds,
        ): KafkaDataQueue<R, T> {
            val pProps = Properties().apply {
                putAll(producerProps)
                put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
                put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
                put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer::class.java.name)
            }
            val cProps = Properties().apply {
                putAll(consumerProps)
                put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
                put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId)
                put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
                put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer::class.java.name)
                putIfAbsent(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
                putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            }
            return KafkaDataQueue(
                producer = KafkaProducer(pProps),
                consumer = KafkaConsumer(cProps),
                topic = topic,
                deserializer = deserializer,
                recordKeyToPayloadType = recordKeyToPayloadType,
                routeToRecordKey = routeToRecordKey,
                capacity = capacity,
                pollTimeout = pollTimeout,
                shutdownTimeout = shutdownTimeout,
            )
        }
    }
}





