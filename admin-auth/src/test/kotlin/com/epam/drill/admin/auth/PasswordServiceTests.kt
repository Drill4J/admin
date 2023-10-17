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
import com.epam.drill.admin.auth.service.impl.PasswordServiceImpl
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class GeneratePasswordTest {

    @Test
    fun `given minLength 10 generatePassword result must be at least 10 characters`() {
        val passwordService = PasswordServiceImpl(minLength = 10)
        val password = passwordService.generatePassword()
        assertTrue { password.length >= 10 }
    }

    @Test
    fun `given mustHaveUppercase set to true generatePassword result must contain uppercase characters`() {
        val passwordService = PasswordServiceImpl(mustHaveUppercase = true)
        val password = passwordService.generatePassword()
        assertTrue { password.any { it.isUpperCase() } }
    }

    @Test
    fun `given mustHaveLowercase set to true generatePassword result must contain lowercase characters`() {
        val passwordService = PasswordServiceImpl(mustHaveLowercase = true)
        val password = passwordService.generatePassword()
        assertTrue { password.any { it.isLowerCase() } }
    }

    @Test
    fun `given mustHaveDigits set to true generatePassword result must contain digits`() {
        val passwordService = PasswordServiceImpl(mustHaveDigit = true)
        val password = passwordService.generatePassword()
        assertTrue { password.any { it.isDigit() } }
    }
}

class HashPasswordTest {
    @Test
    fun `given hashed password hashPassword must pass verification checkPassword`() {
        val passwordService = PasswordServiceImpl()
        val password = "secret"

        val hashedPassword = passwordService.hashPassword(password)
        val valid = passwordService.checkPassword(password, hashedPassword)

        assertTrue { valid }
    }

}

class ValidatePasswordTest {
    @Test
    fun `given less than 10 characters password validatePassword must throw PasswordConstraintsException`() {
        val passwordService = PasswordServiceImpl(minLength = 10)
        assertThrows<PasswordConstraintsException> { passwordService.validatePassword("less10") }
    }

    @Test
    fun `given password without upper case characters validatePassword must throw PasswordConstraintsException`() {
        val passwordService = PasswordServiceImpl(mustHaveUppercase = true)
        assertThrows<PasswordConstraintsException> { passwordService.validatePassword("onlylowercase") }
    }

    @Test
    fun `given password without lower case characters validatePassword must throw PasswordConstraintsException`() {
        val passwordService = PasswordServiceImpl(mustHaveLowercase = true)
        assertThrows<PasswordConstraintsException> { passwordService.validatePassword("ONLYUPPERCASE") }
    }

    @Test
    fun `given password without digits characters validatePassword must throw PasswordConstraintsException`() {
        val passwordService = PasswordServiceImpl(mustHaveDigit = true)
        assertThrows<PasswordConstraintsException> { passwordService.validatePassword("AlphabeticCharsOnly") }
    }

    @Test
    fun `given password with all required characters validatePassword should not throw exceptions`() {
        val passwordService = PasswordServiceImpl(
            minLength = 10,
            mustHaveUppercase = true,
            mustHaveLowercase = true,
            mustHaveDigit = true)

        try {
            passwordService.validatePassword("ABCabc12345")
        } catch (ignore: Exception) {
            fail()
        }
    }

}