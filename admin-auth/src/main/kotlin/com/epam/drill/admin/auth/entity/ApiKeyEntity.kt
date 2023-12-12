package com.epam.drill.admin.auth.entity

import java.time.LocalDateTime

data class ApiKeyEntity(
    val id: Int? = null,
    val user: UserEntity,
    val description: String,
    val apiKeyHash: String,
    val expiresAt: LocalDateTime,
    val createdAt: LocalDateTime? = null
)