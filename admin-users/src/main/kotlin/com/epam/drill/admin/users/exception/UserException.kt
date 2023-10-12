package com.epam.drill.admin.users.exception

sealed class UserException(message: String): RuntimeException(message) {
}