package com.epam.drill.admin.users.exception

class UserNotFoundException(username: String): UserValidationException("User '$username' not found") {
}