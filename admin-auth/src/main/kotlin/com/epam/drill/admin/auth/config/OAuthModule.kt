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

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.epam.drill.admin.auth.principal.Role
import com.epam.drill.admin.auth.principal.User
import com.epam.drill.admin.auth.principal.UserSession
import com.epam.drill.admin.auth.route.unauthorizedError
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
import io.ktor.http.auth.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import kotlinx.serialization.json.*
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton
import org.kodein.di.ktor.closestDI
import java.net.URI
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

const val JWT_COOKIE = "jwt"
const val SESSION_COOKIE = "drill_session"

fun Application.configureOAuthSessions() {
    install(Sessions) {
        cookie<UserSession>(
            SESSION_COOKIE,
            storage = CacheStorage(SessionStorageMemory(), 60.minutes.inWholeMilliseconds)
        ) {
            cookie.path = "/"
            cookie.maxAge = 60.minutes
        }
    }
}

fun DI.MainBuilder.configureOAuthDI() {
    bind<HttpClient>("oauthHttpClient") with singleton { HttpClient(Apache) }
    bind<OAuthConfig>() with singleton { OAuthConfig(di) }
    bind<JwkProvider>() with singleton { buildJwkProvider(instance()) }
}

fun Authentication.Configuration.configureOAuthAuthentication(di: DI) {
    val oauthConfig by di.instance<OAuthConfig>()
    val httpClient by di.instance<HttpClient>("oauthHttpClient")
    val jwkProvider by di.instance<JwkProvider>()

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
        verifier(jwkProvider, oauthConfig.issuer)
        validate { credential ->
            credential.toPrincipal()
        }
        authHeader { call ->
            val headerValue =
                call.request.headers[HttpHeaders.Authorization] ?: "Bearer ${call.request.cookies[JWT_COOKIE]}"
            parseAuthorizationHeader(headerValue)
        }
    }

    oauth("oauth") {
        urlProvider = { URI(oauthConfig.uiRootUrl).resolve("/oauth/callback").toString() }
        providerLookup = {
            OAuthServerSettings.OAuth2ServerSettings(
                name = "oauth2",
                authorizeUrl = oauthConfig.authorizeUrl,
                accessTokenUrl = oauthConfig.accessTokenUrl,
                requestMethod = HttpMethod.Post,
                clientId = oauthConfig.clientId,
                clientSecret = oauthConfig.clientSecret,
                defaultScopes = oauthConfig.scopes
            )
        }
        client = httpClient
    }
}

fun Routing.configureOAuthRoutes() {
    val oauthConfig by closestDI().instance<OAuthConfig>()
    val httpClient by closestDI().instance<HttpClient>("oauthHttpClient")

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

            val userInfo = getUserInfo(httpClient, oauthConfig.userInfoUrl, oauthPrincipal.accessToken)
            if (userInfo == null) {
                call.unauthorizedError()
                return@get
            }

            call.sessions.set(UserSession(userInfo.toPrincipal(), oauthPrincipal.accessToken, oauthPrincipal.refreshToken))
            call.response.cookies.append(
                Cookie(
                    JWT_COOKIE,
                    oauthPrincipal.accessToken,
                    httpOnly = true,
                    path = oauthConfig.uiRootPath
                )
            )
            call.respondRedirect(oauthConfig.uiRootUrl)
        }
    }
}

private suspend fun getUserInfo(
    httpClient: HttpClient,
    userInfoUrl: String,
    accessToken: String
): JsonElement? {
    val response =
        httpClient.get<HttpResponse>(userInfoUrl) {
            headers {
                append(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        }
    return response.status.takeIf { it.isSuccess() }?.let { _ ->
        val stringBody = response.receive<String>()
        val jsonBody = Json.parseToJsonElement(stringBody)
        jsonBody
    }
}

private fun JsonElement.toPrincipal(): User {
    val jsonObject = this.jsonObject
    return User(
        username = jsonObject.getValue("preferred_username").jsonPrimitive.content,
        role = jsonObject["roles"]?.jsonArray?.map { it.jsonPrimitive.content }.let { findRole(it ?: emptyList()) }
    )
}

private fun JWTCredential.toPrincipal() = User(
    username = this["preferred_username"].toString(),
    role = findRole(this.payload.getClaim("realm_access").asMap()["roles"] as List<String>)
)

private fun findRole(roleNames: List<String>): Role {
    return Role.values().find { role ->
        roleNames.toSet().map { it.lowercase() }.contains(role.name.lowercase())
    } ?: Role.UNDEFINED
}

private fun buildJwkProvider(oauthConfig: OAuthConfig): JwkProvider {
    return JwkProviderBuilder(URL(oauthConfig.jwkSetUrl))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()
}