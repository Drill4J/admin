package com.epam.drill.admin.auth.service

/**
 * A service for generating passwords.
 */
interface PasswordGenerator {
    /**
     * Generates a password.
     * @return the generated password
     */
    fun generatePassword(): String
}