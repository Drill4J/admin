package com.epam.drill.admin.jwt.config

import com.auth0.jwt.*
import com.auth0.jwt.algorithms.*
import java.util.*

object JwtAuth {
    private val accessTokenConfig = JwtConfig(TokenType.Access)
    private val refreshTokenConfig = JwtConfig(TokenType.Refresh)

    fun verifier(typeOfToken: TokenType): JWTVerifier =
        when (typeOfToken) {
            is TokenType.Access -> getVerifier(accessTokenConfig.algorithm)
            is TokenType.Refresh -> getVerifier(refreshTokenConfig.algorithm)
        }

    private fun getVerifier(algorithm: Algorithm) = JWT
        .require(algorithm)
        .withIssuer(JwtConfig.issuer)
        .build()

    fun makeToken(userId: Int, role: String, typeOfToken: TokenType): String =
        when (typeOfToken) {
            is TokenType.Access -> {
                createToken(
                    userId to role,
                    typeOfToken to accessTokenConfig.algorithm
                )
            }
            is TokenType.Refresh -> {
                createToken(
                    userId to role,
                    typeOfToken to refreshTokenConfig.algorithm
                )
            }
        }

    private fun createToken(
        userData: Pair<Int, String>,
        typeToAlgorithm: Pair<TokenType, Algorithm>
    ): String {
        return JWT.create()
            .withSubject(typeToAlgorithm.first.name)
            .withIssuer(JwtConfig.issuer)
            .withClaim("id", userData.first)
            .withClaim("role", userData.second)
            .withExpiresAt(getExpiration(typeToAlgorithm.first))
            .sign(typeToAlgorithm.second)
    }

    private fun getExpiration(typeOfToken: TokenType) =
        when (typeOfToken) {
            is TokenType.Access -> Date(System.currentTimeMillis() + accessTokenConfig.validity)
            is TokenType.Refresh -> Date(System.currentTimeMillis() + refreshTokenConfig.validity)
        }

    fun refreshToken(token: String): String {
        val decodedJwt = JWT.decode(token)
        val userId = decodedJwt.claims["id"]!!.asInt()
        val userRole = decodedJwt.claims["role"].toString()
        return makeToken(userId, userRole, TokenType.Access)
    }

}
