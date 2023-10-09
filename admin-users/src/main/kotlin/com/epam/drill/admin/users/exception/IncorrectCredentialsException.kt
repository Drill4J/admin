package com.epam.drill.admin.users.exception

class IncorrectCredentialsException : UserValidationException("Username or password is incorrect") {
}