package com.epam.drill.admin.jwt.validation

import com.auth0.jwt.exceptions.*
import com.epam.drill.admin.*
import com.epam.drill.admin.jwt.config.*
import com.epam.drill.admin.jwt.storage.*

class RefreshTokenValidator(private val tokenManager: TokenManager) {

    suspend fun validate(token: String): ValidationResult =
        try {
            if (tokenManager.containToken(token)) {
                val decoded = JwtAuth.verifier(TokenType.Refresh).verify(token)
                if (decoded.claims["id"]?.asInt()?.let(userSource::findUserById) == null) {
                    ValidationResult.NO_SUCH_USER
                } else ValidationResult.OK
            } else {
                ValidationResult.NO_SUCH_TOKEN
            }
        } catch (e: JWTVerificationException) {
            ValidationResult.VERIFY_EXCEPTION
        }
}

enum class ValidationResult {
    NO_SUCH_TOKEN, VERIFY_EXCEPTION, NO_SUCH_USER, OK
}
