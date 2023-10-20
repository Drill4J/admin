package com.epam.drill.admin.auth.service.impl

import com.epam.drill.admin.auth.config.PasswordRequirementsConfig
import com.epam.drill.admin.auth.exception.UserValidationException
import com.epam.drill.admin.auth.service.PasswordValidator

class PasswordValidatorImpl(
    private val minLength: Int = 6,
    private val mustHaveUppercase: Boolean = false,
    private val mustHaveLowercase: Boolean = false,
    private val mustHaveDigit: Boolean = false
) : PasswordValidator {
    private val hasAtLeastMinLength = { password: String -> password.length >= minLength }
    private val hasUppercase = { password: String -> password.any { it.isUpperCase() } }
    private val hasLowercase = { password: String -> password.any { it.isLowerCase() } }
    private val hasDigit = { password: String -> password.any { it.isDigit() } }

    constructor(config: PasswordRequirementsConfig) : this(
        minLength = config.minLength,
        mustHaveUppercase = config.mustHaveUppercase,
        mustHaveLowercase = config.mustHaveLowercase,
        mustHaveDigit = config.mustHaveDigit
    )

    override fun validatePasswordRequirements(password: String) {
        if (!hasAtLeastMinLength(password))
            throw UserValidationException("Password must have at least $minLength characters")
        if (mustHaveUppercase && !hasUppercase(password))
            throw UserValidationException("Password must contain uppercase letters")
        if (mustHaveLowercase && !hasLowercase(password))
            throw UserValidationException("Password must contain lowercase letters")
        if (mustHaveDigit && !hasDigit(password))
            throw UserValidationException("Password must contain numbers")
    }
}