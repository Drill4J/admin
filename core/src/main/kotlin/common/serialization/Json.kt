package com.epam.drill.admin.common.serialization

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.*

val json = Json(JsonConfiguration.Stable)

infix fun <T> KSerializer<T>.parse(rawData: String): T = json.parse(this, rawData)
infix fun <T> KSerializer<T>.stringify(rawData: T) = json.stringify(this, rawData)

fun String.parseJson(): JsonElement = json.parseJson(this)

fun Any.jsonStringify(): String = json.stringify(
    json.context.getContextualOrDefault(this),
    this
)
