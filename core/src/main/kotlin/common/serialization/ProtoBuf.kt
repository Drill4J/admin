package com.epam.drill.admin.common.serialization

import kotlinx.serialization.*

inline fun <reified T> BinaryFormat.dump(
    value: T
): ByteArray = encodeToByteArray(serializer(), value)

fun <T> BinaryFormat.dump(
    serializer: SerializationStrategy<T>,
    value: T
): ByteArray = encodeToByteArray(serializer, value)

fun <T> BinaryFormat.dumps(
    serializer: SerializationStrategy<T>,
    value: T
): String = encodeToHexString(serializer, value)

fun <T> BinaryFormat.load(
    deserializer: DeserializationStrategy<T>,
    bytes: ByteArray
): T = decodeFromByteArray(deserializer, bytes)

fun <T> BinaryFormat.loads(
    deserializer: DeserializationStrategy<T>,
    string: String
): T = decodeFromHexString(deserializer, string)
