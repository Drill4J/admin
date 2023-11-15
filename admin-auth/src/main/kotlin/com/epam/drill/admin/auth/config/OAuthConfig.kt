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

import io.ktor.application.*
import io.ktor.config.*
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance

class OAuthConfig(override val di: DI) : DIAware {
    private val app by instance<Application>()

    private val drill: ApplicationConfig
        get() = app.environment.config
            .config("drill")

    private val ui: ApplicationConfig
        get() = drill.config("ui")

    private val oauth2: ApplicationConfig
        get() = drill
            .config("auth")
            .config("oauth2")

    val authorizeUrl: String
        get() = oauth2.property("authorizeUrl").getString()

    val accessTokenUrl: String
        get() = oauth2.property("accessTokenUrl").getString()

    val userInfoUrl: String
        get() = oauth2.property("userInfoUrl").getString()

    val jwkSetUrl: String
        get() = oauth2.property("jwkSetUrl").getString()

    val clientId: String
        get() = oauth2.property("clientId").getString()

    val clientSecret: String
        get() = oauth2.property("clientSecret").getString()

    val issuer: String
        get() = oauth2.property("issuer").getString()

    val scopes: List<String>
        get() = oauth2.propertyOrNull("scopes")?.getString()
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: listOf()

    val uiRootUrl: String
        get() =  ui.propertyOrNull("rootUrl")?.getString() ?: "http://localhost:9090"

    val uiRootPath: String
        get() = ui.propertyOrNull("rootPath")?.getString() ?: "/"
}
