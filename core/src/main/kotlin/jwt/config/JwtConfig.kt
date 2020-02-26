package com.epam.drill.admin.jwt.config

import com.auth0.jwt.*
import com.auth0.jwt.algorithms.*
import com.epam.drill.admin.jwt.user.*

object JwtConfig {
    private const val secret = "HDZZh35d82zdzHJFF86tt"
    private const val issuser = "http://drill-4-j/"
    private val algorithm = Algorithm.HMAC512(secret)

    val verifier: JWTVerifier = JWT
        .require(algorithm)
        .withIssuer(issuser)
        .build()

    fun makeToken(user: User): String = JWT.create()
        .withSubject("Authentication")
        .withIssuer(issuser)
        .withClaim("id", user.id)
        .withClaim("role", user.role)
        .sign(algorithm)

}
