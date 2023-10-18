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

import com.epam.drill.admin.auth.exception.PasswordConstraintsException
import com.epam.drill.admin.auth.service.impl.PasswordGeneratorImpl
import com.epam.drill.admin.auth.service.impl.PasswordServiceImpl
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
        val generator = PasswordGeneratorImpl(mustHaveUppercase = true)
        val password = generator.generatePassword()
        assertTrue { password.any { it.isUpperCase() } }
    }

    @Test
    fun `given mustHaveLowercase set to true, generatePassword result must contain lowercase characters`() {
        val generator = PasswordGeneratorImpl(mustHaveLowercase = true)
        val password = generator.generatePassword()
        assertTrue { password.any { it.isLowerCase() } }
    }

    @Test
    fun `given mustHaveDigits set to true, generatePassword result must contain digits`() {
        val generator = PasswordGeneratorImpl(mustHaveDigit = true)
        val password = generator.generatePassword()
        assertTrue { password.any { it.isDigit() } }
    }
}

class PasswordHashingTest {
    @Test
    fun `given correct hashed password, hashPassword must pass verification checkPassword`() {
        val passwordService = PasswordServiceImpl(mock())
        val password = "secret"

        val hashedPassword = passwordService.hashPassword(password)
        val valid = passwordService.matchPasswords(password, hashedPassword)

        assertTrue { valid }
    }

}

class ValidatePasswordTest {
    @Test
    fun `given less than 10 characters password, validatePassword must fail`() {
        val validator = PasswordGeneratorImpl(minLength = 10)
        assertThrows<PasswordConstraintsException> { validator.validatePasswordRequirements("less10") }
    }

    @Test
    fun `given password without upper case characters, validatePassword must fail`() {
        val validator = PasswordGeneratorImpl(mustHaveUppercase = true)
        assertThrows<PasswordConstraintsException> { validator.validatePasswordRequirements("onlylowercase") }
    }

    @Test
    fun `given password without lower case characters, validatePassword must fail`() {
        val validator = PasswordGeneratorImpl(mustHaveLowercase = true)
        assertThrows<PasswordConstraintsException> { validator.validatePasswordRequirements("ONLYUPPERCASE") }
    }

    @Test
    fun `given password without digits characters, validatePassword must fail`() {
        val validator = PasswordGeneratorImpl(mustHaveDigit = true)
        assertThrows<PasswordConstraintsException> { validator.validatePasswordRequirements("AlphabeticCharsOnly") }
    }

    @Test
    fun `given password satisfying all requirements, validatePassword must succeed`() {
        val validator = PasswordGeneratorImpl(
            minLength = 10,
            mustHaveUppercase = true,
            mustHaveLowercase = true,
            mustHaveDigit = true)

        assertDoesNotThrow { validator.validatePasswordRequirements("ABCabc12345") }
    }

    @Test
    fun `given password with non-latin characters, validatePassword must succeed`() {
        val validator = PasswordGeneratorImpl(mustHaveLowercase = true, mustHaveUppercase = true)
        assertDoesNotThrow { validator.validatePasswordRequirements("Котик123") }
    }

}