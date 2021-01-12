package com.epam.drill.admin.api.websocket

import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
class ListOutput(
    val totalCount: Int,
    val filteredCount: Int,
    val items: List<JsonElement>
)
