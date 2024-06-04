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
package com.epam.drill.admin.auth.route

import com.epam.drill.admin.auth.service.ApiKeyService
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.resources.get
import io.ktor.server.resources.delete
import io.ktor.server.routing.*
import org.kodein.di.instance
import org.kodein.di.ktor.closestDI

@Resource("/keys")
class ApiKeys {
    @Resource("/{id}")
    class Id(val parent: ApiKeys, val id: Int)
}

/**
 * Management API keys routes configuration.
 */
fun Route.apiKeyManagementRoutes() {
    getAllApiKeysRoute()
    deleteApiKeyRoute()
}

/**
 * A route for getting all API keys.
 */
fun Route.getAllApiKeysRoute() {
    val apiKeyService by closestDI().instance<ApiKeyService>()

    get<ApiKeys> {
        val allUserKeys = apiKeyService.getAllApiKeys()
        call.ok(allUserKeys)
    }
}

/**
 * A route for deleting API keys.
 */
fun Route.deleteApiKeyRoute() {
    val apiKeyService by closestDI().instance<ApiKeyService>()

    delete<ApiKeys.Id> { params ->
        val id = params.id
        apiKeyService.deleteApiKey(id)
        call.ok("API key successfully deleted.")
    }
}