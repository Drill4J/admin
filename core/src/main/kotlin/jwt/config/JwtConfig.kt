package com.epam.drill.admin.jwt.config

import com.auth0.jwt.*
import com.auth0.jwt.algorithms.*
import com.epam.drill.admin.config.*
import com.epam.drill.admin.jwt.user.*
import io.ktor.application.*
import io.ktor.config.*
import java.util.*
import kotlin.time.*

val Application.jwtConfig: ApplicationConfig
    get() = environment.config.config("jwt")


val Application.jwtLifetime: Duration
    get() = jwtConfig.propertyOrNull("lifetime")?.getDuration() ?: 15.minutes

object JwtConfig {
    private const val secret = "HDZZh35d82zdzHJFF86tt"
    private const val issuser = "http://drill-4-j/"
    private val algorithm = Algorithm.HMAC512(secret)

    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(issuser)
        .build()

    fun makeToken(user: User, lifetime: Duration): String = JWT.create()
        .withSubject("Authentication")
        .withIssuer(issuser)
        .withClaim("id", user.id)
        .withClaim("role", user.role)
        .withExpiresAt(lifetime.toExpiration())
        .sign(algorithm)
}

private fun Duration.toExpiration() = Date(System.currentTimeMillis() + toLongMilliseconds())

