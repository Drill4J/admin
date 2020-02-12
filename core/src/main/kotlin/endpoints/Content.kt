package com.epam.drill.admin.endpoints

import kotlinx.serialization.*

@Serializable
data class ErrorResponse(val message: String)

@Serializable
data class StatusResponse(
    val code: Int,
    @ContextualSerialization val data: Any
)
