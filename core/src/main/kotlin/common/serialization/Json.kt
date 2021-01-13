package com.epam.drill.admin.common.serialization

import kotlinx.serialization.*
import kotlinx.serialization.json.*

private val json = Json(JsonConfiguration.Stable)

infix fun <T> KSerializer<T>.parse(rawData: String): T = json.parse(this, rawData)
infix fun <T> KSerializer<T>.stringify(rawData: T) = json.stringify(this, rawData)

infix fun <T> KSerializer<T>.toJson(rawData: T): JsonElement = json.toJson(this, rawData)
infix fun <T> KSerializer<T>.fromJson(jsonElem: JsonElement): T = json.fromJson(this, jsonElem)

fun String.parseJson(): JsonElement = json.parseJson(this)
fun Any?.toJson(): JsonElement = this?.let { any ->
    any::class.serializer().cast().let { serializer ->
        serializer toJson any
    }
} ?: JsonNull
fun Iterable<Any>.toJsonList(): List<JsonElement> = firstOrNull()?.let { first ->
    val serializer = first::class.serializer().cast()
    map { serializer toJson it }
}.orEmpty()

@Suppress("NOTHING_TO_INLINE", "UNCHECKED_CAST")
internal inline fun <T> KSerializer<out T>.cast(): KSerializer<T> = this as KSerializer<T>
