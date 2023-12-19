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

import com.epam.drill.admin.auth.model.toPrincipal
import com.epam.drill.admin.auth.repository.ApiKeyRepository
import com.epam.drill.admin.auth.repository.impl.DatabaseApiKeyRepository
import com.epam.drill.admin.auth.service.ApiKeyBuilder
import com.epam.drill.admin.auth.service.ApiKeyService
import com.epam.drill.admin.auth.service.impl.ApiKeyBuilderImpl
import com.epam.drill.admin.auth.service.impl.ApiKeyServiceImpl
import com.epam.drill.admin.auth.service.impl.RandomHexSecretGenerator
import com.epam.drill.admin.auth.service.transaction.TransactionalApiKeyService
import io.ktor.application.*
import io.ktor.auth.*
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton


/**
 * The DI module including API key services.
 */
val apiKeyServicesDIModule = DI.Module("apiKeyServices") {
    importOnce(passwordHashServiceDIModule)
    bind<ApiKeyConfig>() with singleton {
        ApiKeyConfig(instance<Application>().environment.config.config("drill.auth.apiKey"))
    }
    bind<ApiKeyRepository>() with singleton { DatabaseApiKeyRepository() }
    bind<ApiKeyBuilder>() with singleton { ApiKeyBuilderImpl() }
    bind<ApiKeyService>() with singleton {
        TransactionalApiKeyService(
            ApiKeyServiceImpl(
                repository = instance(),
                secretService = instance(),
                apiKeyBuilder = instance(),
                secretGenerator = RandomHexSecretGenerator(instance<ApiKeyConfig>().secretLength)
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