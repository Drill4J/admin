package com.epam.drill.admin.auth.service

import com.epam.drill.admin.auth.exception.UserValidationException

/**
 * A service for validating passwords.
 */
interface PasswordValidator {
    /**
     * Checks the given password for requirements.
     * @param password the password to be validated
     * @exception UserValidationException if the password does not meet the requirements
     */
    fun validatePasswordRequirements(password: String)
}