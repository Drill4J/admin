package com.epam.drill.admin.users

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.locations.*
import io.ktor.serialization.*
import org.kodein.di.*

fun testApp(extraBindings: DI.MainBuilder.() -> Unit = {}): Application.() -> Unit = {
    install(Locations)
    install(ContentNegotiation) {
        json()
    }
    val app = this
    DI {
        bind<Application>() with singleton { app }
        userServicesConfig()
        userRoutesConfig()
        extraBindings()
    }
}