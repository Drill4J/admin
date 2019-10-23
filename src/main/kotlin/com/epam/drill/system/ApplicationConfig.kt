package com.epam.drill.system

import io.ktor.application.*

fun Application.securePort(): String {
    val sslPort = environment.config
        .config("ktor")
        .config("deployment")
        .property("sslPort")
        .getString()
    return sslPort
}

fun Application.isDevMode(): Boolean {
    val dev = environment.config
        .config("ktor").property("dev").getString()
    return dev == "true"
}