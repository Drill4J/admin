package com.epam.drill.admin.users

import com.epam.drill.admin.users.exception.ValidationException
import io.ktor.application.*

inline fun <reified T : Any> ApplicationCall.getRequiredParam(name: String): T {
    val value = parameters[name] ?: throw ValidationException("Query parameter $name is not specified")
    return when (T::class) {
        String::class -> value as T
        Int::class -> value.toInt() as T
        Double::class -> value.toDouble() as T
        Boolean::class -> value.toBoolean() as T
        else -> throw UnsupportedOperationException("Cannot convert String value to type ${T::class}")
    }
}