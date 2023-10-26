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
package com.epam.drill.admin.auth

import com.epam.drill.admin.auth.exception.UserValidationException
import com.epam.drill.admin.auth.service.impl.PasswordGeneratorImpl
import com.epam.drill.admin.auth.service.impl.PasswordServiceImpl
import com.epam.drill.admin.auth.service.impl.PasswordValidatorImpl
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import kotlin.test.Test
import kotlin.test.assertTrue

class PasswordGeneratorTest {

    @Test
    fun `given minLength set to 10, generatePassword result must contain 10 characters`() {
        val generator = PasswordGeneratorImpl(minLength = 10)
        val password = generator.generatePassword()
        assertTrue { password.length == 10 }
    }

    @Test
    fun `given mustHaveUppercase set to true, generatePassword result must contain uppercase characters`() {
        val generator = PasswordGeneratorImpl(mustContainUppercase = true)
        val password = generator.generatePassword()
        assertTrue { password.any { it.isUpperCase() } }
    }

    @Test
    fun `given mustHaveLowercase set to true, generatePassword result must contain lowercase characters`() {
        val generator = PasswordGeneratorImpl(mustContainLowercase = true)
        val password = generator.generatePassword()
        assertTrue { password.any { it.isLowerCase() } }
    }

    @Test
    fun `given mustHaveDigits set to true, generatePassword result must contain digits`() {
        val generator = PasswordGeneratorImpl(mustContainDigit = true)
        val password = generator.generatePassword()
        assertTrue { password.any { it.isDigit() } }
    }
}

class PasswordHashingTest {
    @Test
    fun `given password and its hash matchPasswords must return true`() {
        val passwordService = PasswordServiceImpl(mock(), mock())
        val password = "secret"

        val hashedPassword = passwordService.hashPassword(password)
        val valid = passwordService.matchPasswords(password, hashedPassword)

        assertTrue { valid }
    }

}

class ValidatePasswordTest {
    @Test
    fun `given less than 10 characters password validatePassword must fail`() {
        val validator = PasswordValidatorImpl(minLength = 10)
        assertThrows<UserValidationException> { validator.validatePasswordRequirements("less10") }
    }

    @Test
    fun `given password without upper case characters validatePassword must fail`() {
        val validator = PasswordValidatorImpl(mustContainUppercase = true)
        assertThrows<UserValidationException> { validator.validatePasswordRequirements("onlylowercase") }
    }

    @Test
    fun `given password without lower case characters validatePassword must fail`() {
        val validator = PasswordValidatorImpl(mustContainLowercase = true)
        assertThrows<UserValidationException> { validator.validatePasswordRequirements("ONLYUPPERCASE") }
    }

    @Test
    fun `given password without digits characters validatePassword must fail`() {
        val validator = PasswordValidatorImpl(mustContainDigit = true)
        assertThrows<UserValidationException> { validator.validatePasswordRequirements("AlphabeticCharsOnly") }
    }

    @Test
    fun `given password satisfying all requirements validatePassword must succeed`() {
        val validator = PasswordValidatorImpl(
            minLength = 10,
            mustContainUppercase = true,
            mustContainLowercase = true,
            mustContainDigit = true)

        assertDoesNotThrow { validator.validatePasswordRequirements("ABCabc12345") }
    }

    @Test
    fun `given password with non-latin characters validatePassword must succeed`() {
        val validator = PasswordValidatorImpl(mustContainLowercase = true, mustContainUppercase = true)
        assertDoesNotThrow { validator.validatePasswordRequirements("Котик123") }
    }

}