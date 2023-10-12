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

    private fun Duration.toExpiration() = Date(System.currentTimeMillis() + inWholeMilliseconds)
}