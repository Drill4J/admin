package com.epam.drill.admin.servicegroup

import com.epam.kodux.*
import kotlinx.serialization.*

@Serializable
data class ServiceGroup(
    @Id val id: String,
    val name: String,
    val description: String = "",
    val environment: String = ""
)
