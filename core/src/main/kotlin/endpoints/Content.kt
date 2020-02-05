package com.epam.drill.admin.endpoints

import kotlinx.serialization.*

@Serializable
data class ErrorResponse(val message: String)
