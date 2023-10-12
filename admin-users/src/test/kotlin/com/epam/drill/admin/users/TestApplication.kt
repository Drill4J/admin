package com.epam.drill.admin.users

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.locations.*
import io.ktor.serialization.*
import org.eclipse.jetty.client.api.Authentication
import org.kodein.di.*

fun testApp(bindings: DI.MainBuilder.() -> Unit = {}): Application.() -> Unit = {
    install(Locations)
    install(ContentNegotiation) {
        json()
    }
    install(Authentication) {
        basic {
            validate {
                UserIdPrincipal(it.name)
            }
        }
    }
    val app = this
    DI {
        bind<Application>() with singleton { app }
        bindings()
    }
}