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
package com.epam.drill.admin.auth.jwt

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.epam.drill.admin.auth.service.TokenService
import com.epam.drill.admin.auth.view.UserView
import java.util.*
import kotlin.time.Duration

class JwtTokenService(
    private val jwtConfig: JwtConfig
) : TokenService {

    val verifier: JWTVerifier = JWT.require(jwtConfig.algorithm)
        .withIssuer(jwtConfig.issuer)
        .build()
    override fun issueToken(user: UserView): String = JWT.create()
        .withSubject(user.username)
        .withIssuer(jwtConfig.issuer)
        .withAudience(jwtConfig.audience)
        .withClaim("role", user.role.name)
        .withExpiresAt(jwtConfig.lifetime.toExpiration())
        .sign(jwtConfig.algorithm)

    override fun verifyToken(token: String) {
        verifier.verify(token)
    }

    private fun Duration.toExpiration() = Date(System.currentTimeMillis() + this.inWholeMilliseconds)
}