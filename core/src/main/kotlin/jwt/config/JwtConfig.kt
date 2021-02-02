/**
 * Copyright 2020 EPAM Systems
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

