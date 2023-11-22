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

import com.epam.drill.admin.auth.service.OAuthService
import com.epam.drill.admin.auth.service.TokenService
import com.epam.drill.admin.auth.service.impl.OAuthServiceImpl
import com.epam.drill.admin.auth.service.transaction.TransactionalOAuthService
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton
import org.kodein.di.ktor.closestDI
import java.net.URI

const val JWT_COOKIE = "jwt"


val oauthDIModule = DI.Module("oauth") {
    configureSimpleAuthDI()
    configureJwtDI()
    configureOAuthDI()
}

fun DI.Builder.configureOAuthDI() {
    bind<HttpClient>("oauthHttpClient") with singleton { HttpClient(Apache) }
    bind<OAuthConfig>() with singleton { OAuthConfig(instance<Application>().environment.config) }
    bind<OAuthService>() with singleton { TransactionalOAuthService(OAuthServiceImpl(
        httpClient = instance("oauthHttpClient"),
        oauthConfig = instance(),
        userRepository = instance()))
    }
}

fun Authentication.Configuration.configureOAuthAuthentication(di: DI) {
    val oauthConfig by di.instance<OAuthConfig>()
    val httpClient by di.instance<HttpClient>("oauthHttpClient")

    configureJwtAuthentication(di)
    basic("basic") {
        realm = "Access to the http(s) services"
        validate {
            null //Basic authentication is not supported for the OAuth2 provider, but must be declared
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
    val tokenService by closestDI().instance<TokenService>()
    val oauthService by closestDI().instance<OAuthService>()

    authenticate("oauth") {
        get("/oauth/login") {
            // Redirects to "authorizeUrl" automatically
        }
        get("/oauth/callback") {
            val oauthPrincipal: OAuthAccessTokenResponse.OAuth2 = call.principal() ?: throw OAuthUnauthorizedException()
            val userInfo = oauthService.signInThroughOAuth(oauthPrincipal)
            val jwt = tokenService.issueToken(userInfo)
            call.response.cookies.append(
                Cookie(
                    JWT_COOKIE,
                    jwt,
                    httpOnly = true,
                    path = oauthConfig.uiRootPath
                )
            )
            call.respondRedirect(oauthConfig.uiRootUrl)
        }
    }
}

class OAuthUnauthorizedException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause)

class OAuthAccessDeniedException(message: String? = null) : RuntimeException(message)

fun StatusPages.Configuration.oauthStatusPages() {
    exception<OAuthUnauthorizedException> { cause ->
        call.respond(HttpStatusCode.Unauthorized, cause.message ?: "Failed to verify authentication through OAuth2 provider")
    }
    exception<OAuthAccessDeniedException> { cause ->
        call.respond(HttpStatusCode.Forbidden, cause.message ?: "Access denied")
    }
}
