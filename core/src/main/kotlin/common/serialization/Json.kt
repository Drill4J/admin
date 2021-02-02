/**
 * Copyright 2020 EPAM Systems
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
package com.epam.drill.admin.common.serialization

import kotlinx.serialization.*
import kotlinx.serialization.json.*

val json = Json { encodeDefaults = true }

infix fun <T> KSerializer<T>.parse(rawData: String): T = json.decodeFromString(this, rawData)
infix fun <T> KSerializer<T>.stringify(rawData: T) = json.encodeToString(this, rawData)

infix fun <T> KSerializer<T>.toJson(rawData: T): JsonElement = json.encodeToJsonElement(this, rawData)
infix fun <T> KSerializer<T>.fromJson(jsonElem: JsonElement): T = json.decodeFromJsonElement(this, jsonElem)

fun String.parseJson(): JsonElement = json.parseToJsonElement(this)
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
