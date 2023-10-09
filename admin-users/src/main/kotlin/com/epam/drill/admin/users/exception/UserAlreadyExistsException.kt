package com.epam.drill.admin.users.exception

class UserAlreadyExistsException(username: String): UserValidationException("User '$username' already exists") {
}