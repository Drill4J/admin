package com.epam.drill.admin.endpoints

import kotlinx.serialization.json.*

fun JsonElement.toContentString(): String = (this as? JsonPrimitive)?.content ?: toString()
