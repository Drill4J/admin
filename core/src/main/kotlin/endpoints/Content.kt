package com.epam.drill.admin.endpoints

import kotlinx.serialization.*

@Serializable
data class ErrorResponse(val message: String)

interface WithStatusCode {
    val code: Int
}

@Serializable
data class StatusMessageResponse(
    override val code: Int,
    val message: String
) : WithStatusCode

@Serializable
data class StatusResponse(
    override val code: Int,
    @ContextualSerialization val data: Any
) : WithStatusCode

fun String.statusMessageResponse(code: Int) = StatusMessageResponse(
    code = code,
    message = this
)
