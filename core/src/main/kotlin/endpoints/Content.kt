package com.epam.drill.admin.endpoints

import com.epam.drill.plugin.api.end.*
import kotlinx.serialization.*

@Serializable
data class ErrorResponse(val message: String)

interface WithStatusCode {
    val code: Int
}

@Serializable
data class StatusResponse(
    override val code: Int,
    @ContextualSerialization val data: Any
) : WithStatusCode

fun ActionResult.statusResponse() = StatusResponse(
    code = code,
    data = data
)
