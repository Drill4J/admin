package com.epam.drill.admin.api.websocket

import kotlinx.serialization.*

@Serializable
class ListOutput(
    val totalCount: Int,
    val filteredCount: Int,
    val items: List<@ContextualSerialization Any>
)
