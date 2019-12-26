package com.epam.drill.admin.jwt.config

import com.auth0.jwt.algorithms.*
import java.util.*

data class JwtConfig(val typeOfToken: TokenType) {
    private val secret: String
    val validity: Long
    val algorithm: Algorithm

    companion object {
        lateinit var issuer: String
    }

    init {
        val property = Properties()
        JwtConfig::class.java.classLoader.getResourceAsStream("jwt.properties").use {
            property.load(it)
        }

        secret = property.getProperty("jwt.secret.${typeOfToken.name}")
        issuer = property.getProperty("jwt.issuer")
        validity = property.getProperty("jwt.validity.${typeOfToken.name}").toLong()
        algorithm = Algorithm.HMAC256(secret)
    }

}

sealed class TokenType(val name: String) {
    object Access : TokenType("access")
    object Refresh : TokenType("refresh")
}
