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
package com.epam.drill.admin.auth

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.locations.*
import io.ktor.routing.*
import io.ktor.serialization.*
import org.kodein.di.*
import org.kodein.di.ktor.di

fun testApp(
    authentication: Authentication.Configuration.() -> Unit = {},
    routing: Routing.() -> Unit = {},
    bindings: DI.MainBuilder.() -> Unit = {}
): Application.() -> Unit = {
    install(Locations)
    install(ContentNegotiation) {
        json()
    }
    install(Authentication) {
        authentication()
    }

    di {
        bindings()
    }

    routing {
        routing()
    }
}