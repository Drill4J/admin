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

import io.ktor.server.application.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.*
import io.ktor.server.request.header
import io.ktor.server.response.respond

const val API_KEY_HEADER = "X-Api-Key"

/**
 * A Ktor provider for API key authentication.
 */
class ApiKeyAuthenticationProvider internal constructor(
    config: Configuration
) : AuthenticationProvider(config) {

    internal val headerName: String = config.headerName
    internal val authenticationFunction = config.authenticationFunction
    internal val challengeFunction = config.challengeFunction
    internal val authScheme = config.authScheme

    /**
     * Api key auth configuration.
     */
    class Configuration internal constructor(name: String?) : Config(name) {

        internal lateinit var authenticationFunction: suspend ApplicationCall.(String) -> Principal?

        internal var challengeFunction: suspend (ApplicationCall) -> Unit = { call ->
            call.respond(HttpStatusCode.Unauthorized)
        }

        /**
         * The scheme name that will be used when challenge fails, see [AuthenticationContext.challenge].
         */
        var authScheme: String = "apiKey"

        /**
         * The header name that will be used as a source for the api key.
         */
        var headerName: String = API_KEY_HEADER

        /**
         * A function that will check given API key retrieved from [headerName] and return [Principal],
         * or null if credential does not correspond to an authenticated principal.
         */
        fun validate(body: suspend ApplicationCall.(String) -> Principal?) {
            authenticationFunction = body
        }

        /**
         * A function that will be used as a response if authentication failed.
         */
        fun challenge(body: suspend (ApplicationCall) -> Unit) {
            challengeFunction = body
        }
    }

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val apiKey = context.call.request.header(headerName)
        val principal = apiKey?.let { authenticationFunction(context.call, it) }

        val challenge: (AuthenticationFailedCause) -> Unit = { cause ->
            context.challenge(authScheme, cause) { challenge, call ->
                challengeFunction(call)
                challenge.complete()
            }
        }

        if (apiKey == null) {
            challenge(AuthenticationFailedCause.NoCredentials)
        } else if (principal == null) {
            challenge(AuthenticationFailedCause.InvalidCredentials)
        } else {
            context.principal(principal)
        }
    }
}

/**
 * Installs API Key authentication mechanism.
 * @param name the name of the provider
 * @param configure the configuration block
 */
fun AuthenticationConfig.apiKey(
    name: String? = null,
    configure: ApiKeyAuthenticationProvider.Configuration.() -> Unit
) {
    val provider = ApiKeyAuthenticationProvider(ApiKeyAuthenticationProvider.Configuration(name).apply(configure))
    register(provider)
}