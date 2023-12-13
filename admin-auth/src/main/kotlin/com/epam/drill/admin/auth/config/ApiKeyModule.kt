package com.epam.drill.admin.auth.config

import com.epam.drill.admin.auth.model.toPrincipal
import com.epam.drill.admin.auth.repository.ApiKeyRepository
import com.epam.drill.admin.auth.repository.impl.DatabaseApiKeyRepository
import com.epam.drill.admin.auth.service.ApiKeyService
import com.epam.drill.admin.auth.service.impl.ApiKeyServiceImpl
import com.epam.drill.admin.auth.service.transaction.TransactionalApiKeyService
import io.ktor.application.*
import io.ktor.auth.*
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton


/**
 * The DI module including all services and configurations for API key authentication.
 */
val apiKeyDIModule = DI.Module("apiKey") {
    passwordServicesConfig()
    configureApiKeyDI()
}

/**
 * A DI Builder extension function registering all Kodein bindings for API key authentication.
 */
fun DI.Builder.configureApiKeyDI() {
    bind<ApiKeyRepository>() with singleton { DatabaseApiKeyRepository() }
    bind<ApiKeyService>() with singleton {
        TransactionalApiKeyService(
            ApiKeyServiceImpl(
                repository = instance(),
                passwordService = instance(),
            )
        )
    }
}

/**
 * A Ktor Authentication configuration for API key authentication.
 */
fun Authentication.Configuration.configureApiKeyAuthentication(di: DI) {
    val apiKeyService by di.instance<ApiKeyService>()

    apiKey("api-key") {
        validate { apiKey ->
            apiKeyService.signInThroughApiKey(apiKey).toPrincipal()
        }
    }
}