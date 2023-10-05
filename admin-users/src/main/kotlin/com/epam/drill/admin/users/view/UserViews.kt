package com.epam.drill.admin.users.view

import com.epam.drill.admin.users.model.Role
import com.epam.drill.admin.users.model.UserStatus
import kotlinx.serialization.*

@Serializable
data class UserView(
    val username: String,
    val role: Role,
    val status: UserStatus
)

@Serializable
data class UserForm(val role: Role)

@Serializable
data class CredentialsView(val username: String, val password: String, val role: Role)
