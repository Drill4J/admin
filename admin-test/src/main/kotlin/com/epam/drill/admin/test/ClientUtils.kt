package com.epam.drill.admin.test

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*


fun TestApplication.drillClient(block: HttpClientConfig<out HttpClientEngineConfig>.() -> Unit = {}): HttpClient {
    return createClient {
        install(Resources)
        install(ContentNegotiation) {
            json()
        }
        defaultRequest {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        }
        block()
    }
}
