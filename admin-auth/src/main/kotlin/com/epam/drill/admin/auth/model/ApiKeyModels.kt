package com.epam.drill.admin.auth.model

import com.epam.drill.admin.auth.principal.Role
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class ApiKeyView(
    val id: Int,
    val userId: Int,
    val description: String,
    val expired: LocalDateTime,
    val created: LocalDateTime,
    val username: String,
    val role: Role
)

@Serializable
data class ApiKeyCredentialsView(
    val id: Int,
    val apiKey: String,
    val expired: LocalDateTime
)

@Serializable
data class GenerateApiKeyPayload(
    val description: String,
    val expiryPeriod: ExpiryPeriod
)

enum class ExpiryPeriod(val months: Int) {
    ONE_MONTH(1),
    THREE_MONTHS(3),
    SIX_MONTHS(6),
    ONE_YEAR(12)
}