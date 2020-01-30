package com.epam.drill.admin.endpoints

import io.ktor.http.*

fun Parameters.asMap() = names().map { name ->
    name to (get(name) ?: "")
}.toMap()
