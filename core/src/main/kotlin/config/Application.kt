package com.epam.drill.admin.config

import io.ktor.application.*
import io.ktor.config.*
import kotlin.time.*

val Application.drillConfig: ApplicationConfig
    get() = environment.config.config("drill")


val Application.isDevMode: Boolean
    get() = drillConfig.property("devMode").getString().toBoolean()

val Application.agentSocketTimeout: Duration
    get() = drillConfig.config("agents")
        .config("socket")
        .property("timeout").getString().toInt().seconds
