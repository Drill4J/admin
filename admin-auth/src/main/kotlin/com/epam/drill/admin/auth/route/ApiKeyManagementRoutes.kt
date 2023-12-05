package com.epam.drill.admin.auth.route

import com.epam.drill.admin.auth.service.ApiKeyService
import io.ktor.application.*
import io.ktor.locations.*
import io.ktor.routing.*
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI

@Location("/keys")
object ApiKeys {
    @Location("/{id}")
    data class Id(val id: Int)
}

fun Routing.apiKeyManagementRoutes() {
    getAllApiKeysRoute()
    deleteApiKeyRoute()
}

fun Route.getAllApiKeysRoute() {
    val apiKeyService by closestDI().instance<ApiKeyService>()

    get<ApiKeys> {
        val allUserKeys = apiKeyService.getAllApiKeys()
        call.ok(allUserKeys)
    }
}

fun Route.deleteApiKeyRoute() {
    val apiKeyService by closestDI().instance<ApiKeyService>()

    delete<ApiKeys.Id> { params ->
        val id = params.id
        apiKeyService.deleteApiKey(id)
        call.ok("API key successfully deleted.")
    }
}