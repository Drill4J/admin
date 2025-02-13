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
package com.epam.drill.admin.test

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.resources.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.kodein.di.DI
import org.kodein.di.ktor.di
import kotlin.test.assertEquals


fun withRollback(test: suspend () -> Unit) {
    runBlocking {
        newSuspendedTransaction {
            try {
                test()
            } finally {
                rollback()
            }
        }
    }
}

fun drillApplication(
    vararg diModules: DI.Module = emptyArray(),
    routes: Routing.() -> Unit
) = TestApplication {
    install(Resources)
    install(ContentNegotiation) {
        json()
    }
    application {
        di {
            diModules.forEach { import(it) }
        }
    }
    routing {
        routes()
    }
}

fun assertJsonEquals(json1: String, json2: String) {
    val json = Json { ignoreUnknownKeys = true }

    val obj1: JsonElement = json.parseToJsonElement(json1)
    val obj2: JsonElement = json.parseToJsonElement(json2)

    assertEquals(obj1, obj2)
}