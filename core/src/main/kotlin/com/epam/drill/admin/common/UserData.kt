package com.epam.drill.admin.common

import kotlinx.serialization.Serializable


@Serializable
data class UserData(
    val name: String,
    val password:String
)
