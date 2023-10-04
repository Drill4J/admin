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
package com.epam.drill.admin.jwt.config

import com.auth0.jwt.*
import com.auth0.jwt.algorithms.*
import com.epam.drill.admin.config.*
import com.epam.drill.admin.jwt.user.*
import io.ktor.application.*
import io.ktor.config.*
import mu.KotlinLogging
import java.util.*
import javax.crypto.KeyGenerator
import kotlin.time.*

val logger = KotlinLogging.logger {}
val Application.jwtProperties: ApplicationConfig
    get() = drillConfig.config("jwt")

val generatedSecret: String by lazy {
    logger.warn { "The generated secret key for the JWT is used. " +
            "To set your secret key, use the DRILL_JWT_SECRET environment variable." }
    generateSecret()
}
val Application.jwtSecret: String
    get() = jwtProperties.propertyOrNull("secret")?.getString() ?: generatedSecret

val Application.jwtIssuer: String
    get() = jwtProperties.propertyOrNull("issuer")?.getString() ?: "Drill4J App"

val Application.jwtLifetime: Duration
    get() = jwtProperties.propertyOrNull("lifetime")?.getDuration() ?: Duration.minutes(15)

val Application.jwtAudience: String?
    get() = jwtProperties.propertyOrNull("audience")?.getString()

val Application.jwtAlgorithm: Algorithm
    get() = Algorithm.HMAC512(jwtSecret)

val Application.jwtConfig: JwtConfig
    get() = JwtConfig(
        algorithm = jwtAlgorithm,
        issuer = jwtIssuer,
        audience = jwtAudience
    )


class JwtConfig(
    private val algorithm: Algorithm,
    private val issuer: String,
    private val audience: String? = null
) {

    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(issuer)
        .build()

    fun makeToken(user: User, lifetime: Duration): String = JWT.create()
        .withSubject("Authentication")
        .withIssuer(issuer)
        .withAudience(audience)
        .withClaim("id", user.id)
        .withClaim("role", user.role)
        .withExpiresAt(lifetime.toExpiration())
        .sign(algorithm)
}

private fun Duration.toExpiration() = Date(System.currentTimeMillis() + inWholeMilliseconds)

private fun generateSecret() = KeyGenerator.getInstance("HmacSHA512").generateKey().encoded.contentToString()