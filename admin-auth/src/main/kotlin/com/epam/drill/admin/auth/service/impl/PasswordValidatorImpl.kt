/**
 * Copyright 2020 - 2022 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.drill.admin.auth.service.impl

import com.epam.drill.admin.auth.config.PasswordStrengthConfig
import com.epam.drill.admin.auth.exception.UserValidationException
import com.epam.drill.admin.auth.service.PasswordValidator

class PasswordValidatorImpl(
    private val minLength: Int = 6,
    private val mustContainUppercase: Boolean = false,
    private val mustContainLowercase: Boolean = false,
    private val mustContainDigit: Boolean = false
) : PasswordValidator {
    private val hasAtLeastMinLength = { password: String -> password.length >= minLength }
    private val hasUppercase = { password: String -> password.any { it.isUpperCase() } }
    private val hasLowercase = { password: String -> password.any { it.isLowerCase() } }
    private val hasDigit = { password: String -> password.any { it.isDigit() } }

    constructor(config: PasswordStrengthConfig) : this(
        minLength = config.minLength,
        mustContainUppercase = config.mustContainUppercase,
        mustContainLowercase = config.mustContainLowercase,
        mustContainDigit = config.mustContainDigit
    )

    override fun validatePasswordRequirements(password: String) {
        if (!hasAtLeastMinLength(password))
            throw UserValidationException("Password must have at least $minLength characters")
        if (mustContainUppercase && !hasUppercase(password))
            throw UserValidationException("Password must contain uppercase letters")
        if (mustContainLowercase && !hasLowercase(password))
            throw UserValidationException("Password must contain lowercase letters")
        if (mustContainDigit && !hasDigit(password))
            throw UserValidationException("Password must contain numbers")
    }
}