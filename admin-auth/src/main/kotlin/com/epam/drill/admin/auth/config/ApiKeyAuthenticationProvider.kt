package com.epam.drill.admin.auth.config

import io.ktor.application.*
import io.ktor.http.HttpStatusCode
import io.ktor.auth.*
import io.ktor.request.header
import io.ktor.response.respond

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
    class Configuration internal constructor(name: String?) : AuthenticationProvider.Configuration(name) {

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
}

/**
 * Installs API Key authentication mechanism.
 * @param name the name of the provider
 * @param configure the configuration block
 */
fun Authentication.Configuration.apiKey(
    name: String? = null,
    configure: ApiKeyAuthenticationProvider.Configuration.() -> Unit
) {
    val provider = ApiKeyAuthenticationProvider(ApiKeyAuthenticationProvider.Configuration(name).apply(configure))

    provider.pipeline.intercept(AuthenticationPipeline.RequestAuthentication) { context ->
        val apiKey = context.call.request.header(provider.headerName)
        val principal = apiKey?.let { provider.authenticationFunction(context.call, it) }

        val cause = when {
            apiKey == null -> AuthenticationFailedCause.NoCredentials
            principal == null -> AuthenticationFailedCause.InvalidCredentials
            else -> null
        }

        if (cause != null) {
            context.challenge(provider.authScheme, cause) { challenge ->
                provider.challengeFunction(context.call)
                challenge.complete()
            }
        }
        if (principal != null) {
            context.principal(principal)
        }
    }
    register(provider)
}