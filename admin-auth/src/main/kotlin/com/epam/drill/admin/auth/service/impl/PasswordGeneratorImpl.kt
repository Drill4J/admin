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

import com.epam.drill.admin.auth.exception.UserValidationException
import com.epam.drill.admin.auth.service.PasswordGenerator
import kotlin.random.Random


const val ALPHABETIC_UPPERCASE_CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
const val ALPHABETIC_LOWERCASE_CHARSET = "abcdefghijklmnopqrstuvwxyz"
const val DIGITS_CHARSET = "0123456789"

class PasswordGeneratorImpl(private val minLength: Int = 6,
                            private val mustHaveUppercase: Boolean = false,
                            private val mustHaveLowercase: Boolean = false,
                            private val mustHaveDigit: Boolean = false) : PasswordGenerator {
    private val random = Random.Default

    private val hasAtLeastMinLength = { password: String -> password.length >= minLength }
    private val hasUppercase = { password: String -> password.any { it.isUpperCase() } }
    private val hasLowercase = { password: String -> password.any { it.isLowerCase() } }
    private val hasDigit = { password: String -> password.any { it.isDigit() } }

    override fun generatePassword(): String {
        val allChars = buildString {
            append(ALPHABETIC_UPPERCASE_CHARSET)
            append(ALPHABETIC_LOWERCASE_CHARSET)
            append(DIGITS_CHARSET)
        }

        val passwordLength = minLength
        val random = Random.Default

        val password = buildString {
            //It is guaranteed that an additional character of each type will be added if required
            if (mustHaveUppercase) append(ALPHABETIC_UPPERCASE_CHARSET[random.nextInt(ALPHABETIC_UPPERCASE_CHARSET.length)])
            if (mustHaveLowercase) append(ALPHABETIC_LOWERCASE_CHARSET[random.nextInt(ALPHABETIC_LOWERCASE_CHARSET.length)])
            if (mustHaveDigit) append(DIGITS_CHARSET[random.nextInt(DIGITS_CHARSET.length)])

            //Fill in the rest of the password with random characters
            repeat(passwordLength - length) {
                append(allChars[random.nextInt(allChars.length)])
            }
        }
        //Shuffle it so that there are not always specific symbols at the beginning
        return password.toList().shuffled().joinToString("")
    }

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