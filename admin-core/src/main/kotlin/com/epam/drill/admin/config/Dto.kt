package com.epam.drill.admin.config

import com.epam.drill.admin.auth.model.AuthConfigView
import kotlinx.serialization.Serializable

@Serializable
data class UIConfigDto(
    val auth: AuthConfigView
)