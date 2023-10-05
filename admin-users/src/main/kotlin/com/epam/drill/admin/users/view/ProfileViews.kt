package com.epam.drill.admin.users.view

import kotlinx.serialization.Serializable

@Serializable
data class LoginForm(val username: String, val password: String)
@Serializable
data class RegistrationForm(val username: String, val password: String)
@Serializable
data class TokenResponse(val token: String)
@Serializable
data class ChangePasswordForm(val oldPassword: String, val newPassword: String)