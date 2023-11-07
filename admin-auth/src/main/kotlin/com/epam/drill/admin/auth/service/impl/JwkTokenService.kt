package com.epam.drill.admin.auth.service.impl

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkException
import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTDecodeException
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.JWTVerifier
import com.epam.drill.admin.auth.config.OAuthConfig
import com.epam.drill.admin.auth.model.UserView
import com.epam.drill.admin.auth.service.TokenService
import io.ktor.auth.jwt.*
import java.net.URL
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.TimeUnit

class JwkTokenService(oauthConfig: OAuthConfig) : TokenService {

    val issuer = "http://localhost:8080/realms/master"

    val provider = JwkProviderBuilder(URL("http://localhost:8080/realms/master/protocol/openid-connect/certs"))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    override fun issueToken(user: UserView): String {
        throw UnsupportedOperationException("Issuing tokens is not supported")
    }

    override fun verifyToken(token: String) {
        getVerifier(provider, issuer, token).verify(token)
    }

    private fun getVerifier(
        jwkProvider: JwkProvider,
        issuer: String? = null,
        token: String
    ): JWTVerifier {
        val jwk = jwkProvider.get(JWT.decode(token).keyId)
        val algorithm = jwk.makeAlgorithm()
        return when (issuer) {
            null -> JWT.require(algorithm)
            else -> JWT.require(algorithm).withIssuer(issuer)
        }.build()
    }
}

/**
 * Copy of internal function [com.auth0.jwk.Jwk.makeAlgorithm]
 */
internal fun Jwk.makeAlgorithm(): Algorithm = when (algorithm) {
    "RS256" -> Algorithm.RSA256(publicKey as RSAPublicKey, null)
    "RS384" -> Algorithm.RSA384(publicKey as RSAPublicKey, null)
    "RS512" -> Algorithm.RSA512(publicKey as RSAPublicKey, null)
    "ES256" -> Algorithm.ECDSA256(publicKey as ECPublicKey, null)
    "ES384" -> Algorithm.ECDSA384(publicKey as ECPublicKey, null)
    "ES512" -> Algorithm.ECDSA512(publicKey as ECPublicKey, null)
    null -> Algorithm.RSA256(publicKey as RSAPublicKey, null)
    else -> throw IllegalArgumentException("Unsupported algorithm $algorithm")
}


