package com.epam.drill.admin.auth.principal

data class UserSession(
    val principal: User,
    val accessToken: String,
    val refreshToken: String? = null
)