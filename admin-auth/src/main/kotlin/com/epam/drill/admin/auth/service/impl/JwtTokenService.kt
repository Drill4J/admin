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
package com.epam.drill.admin.auth.service.impl

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.epam.drill.admin.auth.config.JwtConfig
import com.epam.drill.admin.auth.model.UserInfoView
import com.epam.drill.admin.auth.service.TokenService
import java.util.*
import kotlin.time.Duration

class JwtTokenService(jwtConfig: JwtConfig) : TokenService {

    private val algorithm: Algorithm = Algorithm.HMAC512(jwtConfig.secret)
    private val issuer: String = jwtConfig.issuer
    private val audience: String? = jwtConfig.audience
    private val lifetime: Duration = jwtConfig.lifetime

    override fun issueToken(user: UserInfoView): String = JWT.create()
        .withSubject(user.username)
        .withIssuer(issuer)
        .withAudience(audience)
        .withClaim("role", user.role.name)
        .withExpiresAt(lifetime.toExpiration())
        .sign(algorithm)

    private fun Duration.toExpiration() = Date(System.currentTimeMillis() + this.inWholeMilliseconds)
}