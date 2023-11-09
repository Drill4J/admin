package com.epam.drill.admin.auth

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import com.auth0.jwt.algorithms.Algorithm
import com.epam.drill.admin.auth.config.oauthModule
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import kotlin.test.*

class OAuthModuleTest {

    @Mock
    lateinit var mockJwkProvider: JwkProvider
    @Mock
    lateinit var jwk: Jwk

    private val keyPair = generateRSAKeyPair()

    @BeforeTest
    fun setup() {
        MockitoAnnotations.openMocks(this)
    }

    private val mockEngine = MockEngine { request ->
        when (request.url.encodedPath) {
            "/greeting" -> {
                respondOk("Hello, world!")
            }

            else -> {
                respondBadRequest()
            }
        }
    }

    @Test
    fun `given valid jwt, authentication request must succeed`() {
        val testUsername = "foobar"
        val testJwkKey = "testJwk"

        withTestApplication({
            oauthTestEnvironment()
            oauthModule {
                mockHttpClient()
                mockJwkProvider()
            }
            routing {
                authenticate("jwt") {
                    get("/protected") {
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }) {
            whenever(mockJwkProvider.get(testJwkKey)).thenReturn(jwk)
            whenever(jwk.publicKey).thenReturn(keyPair.public)
            whenever(jwk.algorithm).thenReturn("RS256")

            with(handleRequest(HttpMethod.Get, "/protected") {
                addJwtToken(
                    username = testUsername,
                    algorithm = Algorithm.RSA256(keyPair.public as RSAPublicKey, keyPair.private as RSAPrivateKey)
                ) {
                    withHeader(mapOf("kid" to testJwkKey))
                    withClaim("preferred_username", testUsername)
                    withClaim("realm_access", mapOf("roles" to listOf("USER")))
                }
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    private fun generateRSAKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        return keyPairGenerator.generateKeyPair()
    }

    private fun Application.oauthTestEnvironment() {
        environment {
            put("drill.auth.oauth2.authorizeUrl", "http://oauth.com/authorizeUrl")
            put("drill.auth.oauth2.accessTokenUrl", "http://oauth.com/accessTokenUrl")
            put("drill.auth.oauth2.userInfoUrl", "http://oauth.com/userInfoUrl")
            put("drill.auth.oauth2.jwkSetUrl", "http://oauth.com/jwkSetUrl")
            put("drill.auth.oauth2.clientId", "clientId")
            put("drill.auth.oauth2.clientSecret", "clientSecret")
            put("drill.auth.oauth2.scopes", "scopes")
            put("drill.auth.oauth2.issuer", "issuer")
        }
    }

    private fun DI.MainBuilder.mockHttpClient() {
        bind<HttpClient>("oauthHttpClient", overrides = true) with singleton {
            HttpClient(mockEngine)
        }
    }


    private fun DI.MainBuilder.mockJwkProvider() {
        bind<JwkProvider>(overrides = true) with singleton { mockJwkProvider }
    }

}