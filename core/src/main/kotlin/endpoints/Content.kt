package com.epam.drill.admin.endpoints

import kotlinx.serialization.*
import kotlinx.serialization.json.*

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
    val data: JsonElement
) : WithStatusCode
