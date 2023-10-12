package com.epam.drill.admin.users.service
interface PasswordService {
    fun hashPassword(password: String): String
    fun checkPassword(candidate: String, hashed: String): Boolean
    fun generatePassword(): String
    fun validatePassword(password: String)
}