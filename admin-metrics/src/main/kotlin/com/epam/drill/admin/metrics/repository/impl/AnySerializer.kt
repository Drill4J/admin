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
package com.epam.drill.admin.metrics.repository.impl

import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.postgresql.util.PGobject
import java.sql.Timestamp
import org.postgresql.jdbc.PgArray

object AnySerializer : KSerializer<Any?> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Any")

    override fun serialize(encoder: Encoder, value: Any?) {
        when (value) {
            null -> encoder.encodeNull()
            is String -> encoder.encodeString(value)
            is Int -> encoder.encodeInt(value)
            is Long -> encoder.encodeLong(value)
            is Number -> encoder.encodeDouble(value.toDouble())
            is Boolean -> encoder.encodeBoolean(value)
            is Timestamp -> encoder.encodeString(value.toString())
            is Map<*, *> -> {
                val mapSerializer = MapSerializer(String.serializer(), this)
                encoder.encodeSerializableValue(mapSerializer, value as Map<String, Any?>)
            }
            is PgArray -> {
                val array = value.array
                if (array != null) {
                    val list = (array as Array<*>).map { it as String }
                    val listSerializer = ListSerializer(String.serializer())
                    encoder.encodeSerializableValue(listSerializer, list)
                } else {
                    encoder.encodeNull()
                }
            }
            is List<*> -> {
                val listSerializer = ListSerializer(this)
                encoder.encodeSerializableValue(listSerializer, value)
            }
            is PGobject -> {
                val json = value.value
                if (json != null) {
                    val parsedValue = Json.decodeFromString<Map<String, String>>(json)
                    val mapSerializer = MapSerializer(String.serializer(), this)
                    encoder.encodeSerializableValue(mapSerializer, parsedValue)
                } else {
                    encoder.encodeNull()
                }
            }
            else -> {
                val serializer = value::class.serializerOrNull() as KSerializer<Any?>?
                if (serializer != null)
                    encoder.encodeSerializableValue(serializer, value)
                else
                    throw SerializationException("Unsupported type: ${value::class}")
            }
        }
    }

    override fun deserialize(decoder: Decoder): Any? {
        throw SerializationException("Deserialization is not supported")
    }
}

@Serializable
data class ApiResponse(
    @Serializable(with = AnySerializer::class) val data: Any?
)