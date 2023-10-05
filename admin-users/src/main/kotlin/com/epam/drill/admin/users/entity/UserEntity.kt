package com.epam.drill.admin.users.entity

data class UserEntity(
    val id: Int,
    val username: String,
    val passwordHash: String,
    val role: String,
    val blocked: Boolean,
    val deleted: Boolean
)