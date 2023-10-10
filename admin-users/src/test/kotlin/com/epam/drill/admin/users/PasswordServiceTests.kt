package com.epam.drill.admin.users

import com.epam.drill.admin.users.exception.PasswordConstraintsException
import com.epam.drill.admin.users.service.impl.PasswordService
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class GeneratePasswordTest {

    @Test
    fun `given minLength 10 generatePassword result must be at least 10 characters`() {
        val passwordService = PasswordService(minLength = 10)
        val password = passwordService.generatePassword()
        assertTrue { password.length >= 10 }
    }

    @Test
    fun `given mustHaveUppercase set to true generatePassword result must contain uppercase characters`() {
        val passwordService = PasswordService(mustHaveUppercase = true)
        val password = passwordService.generatePassword()
        assertTrue { password.any { it.isUpperCase() } }
    }

    @Test
    fun `given mustHaveLowercase set to true generatePassword result must contain lowercase characters`() {
        val passwordService = PasswordService(mustHaveLowercase = true)
        val password = passwordService.generatePassword()
        assertTrue { password.any { it.isLowerCase() } }
    }

    @Test
    fun `given mustHaveDigits set to true generatePassword result must contain digits`() {
        val passwordService = PasswordService(mustHaveDigit = true)
        val password = passwordService.generatePassword()
        assertTrue { password.any { it.isDigit() } }
    }
}

class HashPasswordTest {
    @Test
    fun `given hashed password hashPassword must pass verification checkPassword`() {
        val passwordService = PasswordService()
        val password = "secret"

        val hashedPassword = passwordService.hashPassword(password)
        val valid = passwordService.checkPassword(password, hashedPassword)

        assertTrue { valid }
    }

}

class ValidatePasswordTest {
    @Test
    fun `given less than 10 characters password validatePassword must throw PasswordConstraintsException`() {
        val passwordService = PasswordService(minLength = 10)
        assertThrows<PasswordConstraintsException> { passwordService.validatePassword("less10") }
    }

    @Test
    fun `given password without upper case characters validatePassword must throw PasswordConstraintsException`() {
        val passwordService = PasswordService(mustHaveUppercase = true)
        assertThrows<PasswordConstraintsException> { passwordService.validatePassword("onlylowercase") }
    }

    @Test
    fun `given password without lower case characters validatePassword must throw PasswordConstraintsException`() {
        val passwordService = PasswordService(mustHaveLowercase = true)
        assertThrows<PasswordConstraintsException> { passwordService.validatePassword("ONLYUPPERCASE") }
    }

    @Test
    fun `given password without digits characters validatePassword must throw PasswordConstraintsException`() {
        val passwordService = PasswordService(mustHaveDigit = true)
        assertThrows<PasswordConstraintsException> { passwordService.validatePassword("AlphabeticCharsOnly") }
    }

    @Test
    fun `given password with all required characters validatePassword should not throw exceptions`() {
        val passwordService = PasswordService(
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