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

import com.auth0.jwt.interfaces.Payload
import com.epam.drill.admin.auth.model.LoginPayload
import com.epam.drill.admin.auth.model.UserView
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.principal.User
import com.epam.drill.admin.auth.principal.UserSession
import com.epam.drill.admin.auth.route.unauthorizedError
import com.epam.drill.admin.auth.service.TokenService
import com.epam.drill.admin.auth.service.UserAuthenticationService
import com.epam.drill.admin.auth.service.impl.JwkTokenService
import com.epam.drill.admin.auth.service.impl.JwtTokenService
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.auth.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import kotlinx.serialization.json.*
import org.kodein.di.*
import kotlin.time.Duration.Companion.minutes


class SecurityConfig(override val di: DI) : DIAware {
    private val app by instance<Application>()
    private val authService by instance<UserAuthenticationService>()
    private val tokenService by instance<TokenService>()

    init {
        app.install(Authentication) {
//            configureJwt("jwt")
            configureBasic("basic")
            configureOAuth()
        }
        app.install(Sessions) {
            cookie<UserSession>(
                "drill_session",
                storage = CacheStorage(SessionStorageMemory(), 60.minutes.inWholeMilliseconds)
            ) {
                cookie.path = "/"
                cookie.maxAge = 60.minutes
            }
        }
        app.routing {
            oauthRoutes()
        }
    }

    private fun Authentication.Configuration.configureBasic(name: String? = null) {
        basic(name) {
            realm = "Access to the http(s) services"
            validate {
                authService.signIn(LoginPayload(username = it.name, password = it.password)).toPrincipal()
            }
        }
    }

    private fun Authentication.Configuration.configureJwt(name: String? = null) {
        jwt(name) {
            realm = "Access to the http(s) and the ws(s) services"
            verifier((tokenService as JwtTokenService).verifier)
            validate {
                it.payload.toPrincipal()
            }
            authHeader { call ->
                val headerValue = call.request.headers[Authorization]
                    ?: "Bearer ${call.request.cookies["jwt"] ?: call.parameters["token"]}"
                parseAuthorizationHeader(headerValue)
            }
        }
    }

    private fun Authentication.Configuration.configureOAuth() {
        session<UserSession>("session") {
            validate { session ->
                session.principal
            }
            challenge {
                call.respondRedirect("/oauth/login")
            }
        }

        jwt("jwt") {
            realm = "Access to the http(s) and the ws(s) services"
            verifier((tokenService as JwkTokenService).provider, (tokenService as JwkTokenService).issuer)
            validate { credential ->
                credential.toPrincipal()
            }
            authHeader { call ->
                val headerValue = call.request.headers[Authorization] ?: "Bearer ${call.request.cookies["jwt"]}"
                parseAuthorizationHeader(headerValue)
            }
        }

        val keycloakAddress = "http://localhost:8080"
        oauth("oauth") {
            urlProvider = { "http://localhost:8090/oauth/callback" }
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "keycloak",
                    authorizeUrl = "$keycloakAddress/realms/master/protocol/openid-connect/auth",
                    accessTokenUrl = "$keycloakAddress/realms/master/protocol/openid-connect/token",
                    requestMethod = HttpMethod.Post,
                    clientId = "drill4j",
                    clientSecret = "u8pI2hH3dZhsSL1K6kzGsJRQMxj5EcNn",
                    defaultScopes = listOf("roles", "openid")
                )
            }
            client = HttpClient(Apache)
        }
    }
}

fun Routing.oauthRoutes() {
    authenticate("oauth") {
        get("/oauth/login") {
            // Redirects to "authorizeUrl" automatically
        }
        get("/oauth/callback") {
            val oauthPrincipal: OAuthAccessTokenResponse.OAuth2? = call.principal()
            if (oauthPrincipal == null) {
                call.unauthorizedError()
                return@get
            }

            val user = getUserInfo(oauthPrincipal.accessToken)
            if (user == null) {
                call.unauthorizedError()
                return@get
            }

            call.sessions.set(UserSession(user, oauthPrincipal.accessToken, oauthPrincipal.refreshToken))
            call.response.cookies.append(Cookie("jwt", oauthPrincipal.accessToken, httpOnly = true, path = "/"))
            call.respondRedirect("/")
        }
    }
}

private suspend fun getUserInfo(accessToken: String): User? {
    val httpClient = HttpClient(Apache)
    val response =
        httpClient.get<HttpResponse>("http://localhost:8080/realms/master/protocol/openid-connect/userinfo") {
            headers {
                append(Authorization, "Bearer $accessToken")
            }
        }
    return response.status.takeIf { it.isSuccess() }?.let { _ ->
        val stringBody = response.receive<String>()
        val jsonBody = Json.parseToJsonElement(stringBody)
        jsonBody.toPrincipal()
    }
}

private fun JsonElement.toPrincipal(): User {
    val jsonObject = this.jsonObject
    return User(
        username = jsonObject.getValue("preferred_username").jsonPrimitive.content,
        role = findRole(jsonObject.getValue("roles").jsonArray.map { it.jsonPrimitive.content })
    )
}

private fun JWTCredential.toPrincipal() = User(
    username = this["preferred_username"].toString(),
    role = findRole(this.payload.getClaim("realm_access").asMap()["roles"] as List<String>)
)

private fun Payload.toPrincipal(): User {
    return User(
        username = subject,
        role = Role.valueOf(getClaim("role").asString())
    )
}

private fun findRole(roleNames: List<String>): Role {
    return Role.values().find { role ->
        roleNames.toSet().map { it.lowercase() }.contains(role.name.lowercase())
    } ?: Role.UNDEFINED
}

private fun UserView.toPrincipal(): User {
    return User(
        username = username,
        role = role
    )
}
