package com.epam.drill.admin.auth.route

import com.epam.drill.admin.auth.exception.NotAuthenticatedException
import com.epam.drill.admin.auth.model.GenerateApiKeyPayload
import com.epam.drill.admin.auth.principal.User
import com.epam.drill.admin.auth.service.ApiKeyService
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.locations.*
import io.ktor.locations.post
import io.ktor.request.*
import io.ktor.routing.*
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI

@Location("/user-keys")
object UserApiKeys

/**
 * A user API keys routes configuration.
 */
fun Route.userApiKeyRoutes() {
    getUserApiKeysRoute()
    generateUserApiKeyRoute()
    deleteUserApiKeyRoute()
}

/**
 * A route for getting user API keys.
 */
fun Route.getUserApiKeysRoute() {
    val apiKeyService by closestDI().instance<ApiKeyService>()

    get<UserApiKeys> {
        val principal = call.principal<User>() ?: throw NotAuthenticatedException()
        val userApiKeys = apiKeyService.getApiKeysByUser(principal.id)
        call.ok(userApiKeys)
    }
}

/**
 * A route for generating user API keys.
 */
fun Route.generateUserApiKeyRoute() {
    val apiKeyService by closestDI().instance<ApiKeyService>()

    post<UserApiKeys> {
        val principal = call.principal<User>() ?: throw NotAuthenticatedException()
        val payload = call.receive<GenerateApiKeyPayload>()
        val keyCredentials = apiKeyService.generateApiKey(principal.id, payload)
        call.ok(keyCredentials, "API Key successfully generated.")
    }
}

/**
 * A route for deleting user API keys.
 */
fun Route.deleteUserApiKeyRoute() {
    val apiKeyService by closestDI().instance<ApiKeyService>()

    delete<UserApiKeys> {
        val principal = call.principal<User>() ?: throw NotAuthenticatedException()
        apiKeyService.deleteApiKey(principal.id)
        call.ok("API Key successfully deleted.")
    }
}