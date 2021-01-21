package com.epam.drill.admin.endpoints

import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
data class ErrorResponse(val message: String)

@Serializable
sealed class WithStatusCode {
    abstract val code: Int
}

@Serializable
@SerialName("STATUS_MESSAGE")
data class StatusMessageResponse(
    override val code: Int,
    val message: String
) : WithStatusCode()

@Serializable
@SerialName("STATUS")
data class StatusResponse(
    override val code: Int,
    val data: JsonElement
) : WithStatusCode()
