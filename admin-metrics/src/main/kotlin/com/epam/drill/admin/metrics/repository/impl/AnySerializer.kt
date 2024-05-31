package com.epam.drill.admin.metrics.repository.impl

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// TODO remove AnySerializer once report structure is defined with data classes
//  OR at least replace with Jackson
object AnySerializer : KSerializer<Any?> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Any")

    override fun serialize(encoder: Encoder, value: Any?) {
        when (value) {
            null -> encoder.encodeNull()
            is String -> encoder.encodeString(value)
            is Int -> encoder.encodeInt(value)
            is Number -> encoder.encodeDouble(value.toDouble())
            is Boolean -> encoder.encodeBoolean(value)
            is Map<*, *> -> {
                val mapSerializer = MapSerializer(String.serializer(), this)
                encoder.encodeSerializableValue(mapSerializer, value as Map<String, Any?>)
            }
            is List<*> -> {
                val listSerializer = ListSerializer(this)
                encoder.encodeSerializableValue(listSerializer, value)
            }
            else -> throw SerializationException("Unsupported type")
        }
    }

    override fun deserialize(decoder: Decoder): Any? {
        throw SerializationException("Deserialization is not supported")
    }
}

@Serializable
data class ApiResponse(
    @Serializable(with = AnySerializer::class) val data: Map<String, Any?>?
)