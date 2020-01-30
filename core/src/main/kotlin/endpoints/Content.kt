package com.epam.drill.admin.endpoints

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val message: String)
