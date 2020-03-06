package com.epam.drill.admin.config

import io.ktor.application.*
import io.ktor.config.*

val Application.drillConfig: ApplicationConfig
    get() = environment.config.config("drill")


val Application.isDevMode: Boolean
    get() = drillConfig.property("devMode").getString().toBoolean()
