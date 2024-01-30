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
package com.epam.drill.admin.auth.config

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.Payload
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.principal.User
import com.epam.drill.admin.auth.service.TokenService
import com.epam.drill.admin.auth.service.impl.JwtTokenService
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.http.*
import io.ktor.http.auth.*
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton

const val CLAIM_USER_ID = "userId"
const val CLAIM_ROLE = "role"
const val JWT_COOKIE = "jwt"

val jwtServicesDIModule = DI.Module("jwtServices") {
    bind<JwtConfig>() with singleton {
        JwtConfig(instance<Application>().environment.config.config("drill.auth.jwt"))
    }
    bind<JWTVerifier>() with singleton { buildJwkVerifier(instance()) }
    bind<TokenService>() with singleton { JwtTokenService(instance()) }
}

/**
 * A Ktor Authentication plugin configuration for JWT based authentication.
 */
fun Authentication.Configuration.configureJwtAuthentication(di: DI) {
    val jwtVerifier by di.instance<JWTVerifier>()

    jwt("jwt") {
        realm = "Access to the http(s) and the ws(s) services"
        verifier(jwtVerifier)
        validate {
            it.payload.toPrincipal()
        }
        authHeader { call ->
            val headerValue = call.request.headers[HttpHeaders.Authorization]
                ?: "Bearer ${call.request.cookies[JWT_COOKIE] ?: call.parameters["token"]}"
            parseAuthorizationHeader(headerValue)
        }
    }
}

private fun buildJwkVerifier(jwtConfig: JwtConfig) = JWT
    .require(Algorithm.HMAC512(jwtConfig.secret))
    .withIssuer(jwtConfig.issuer)
    .build()

private fun Payload.toPrincipal(): User {
    return User(
        id = getClaim(CLAIM_USER_ID).asInt(),
        username = subject,
        role = Role.valueOf(getClaim(CLAIM_ROLE).asString())
    )
}